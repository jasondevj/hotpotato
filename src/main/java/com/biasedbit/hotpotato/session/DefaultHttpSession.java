/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biasedbit.hotpotato.session;

import com.biasedbit.hotpotato.client.CannotExecuteRequestException;
import com.biasedbit.hotpotato.client.HttpClient;
import com.biasedbit.hotpotato.request.DefaultHttpRequestFuture;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import com.biasedbit.hotpotato.response.TypedDiscardProcessor;
import com.biasedbit.hotpotato.session.handler.AuthenticationResponseHandler;
import com.biasedbit.hotpotato.session.handler.RedirectResponseHandler;
import com.biasedbit.hotpotato.session.handler.ResponseCodeHandler;
import com.biasedbit.hotpotato.util.HostPortAndUri;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default implementation of {@link HttpSession}.
 * <p/>
 * Supports HTTP and optionally HTTPS requests, if a HTTPS client is provided. If no HTTPS client is provided, then
 * requests starting with "https://" will cause in a {@link CannotExecuteRequestException} being thrown whenever a
 * {@linkplain #execute(String, HttpRequest) variant of {@code execute()}} is called.
 * <p/>
 * Provided {@link HttpClient} instances must be pre-initialised and terminated manually.
 *
 * <h2>Cookie handling</h2>
 * Every time a Set-Cookie header is received, instances of this class will preserve these cookies for subsequent
 * requests.
 * <p/>
 * At this time, no cookie management (expiration checks, etc) exists.
 *
 * <h2>Authentication</h2>
 * Support for digest authentication is automatically provided through {@link AuthenticationResponseHandler}.
 * A username and password are configured via {@link #setAuthCredentials(String, String)} and all subsequent 401
 * responses will automatically be dealt with using these credentials.
 * <p/>
 * If these credentials are not provided, 401 responses will result in immediate future release. If the authentication
 * fails once, the second 401 will also yield the same result (future is released).
 *
 * <h2>Thread safety</h2>
 * This class is thread safe although it is <strong>not recommended</strong> to have a session execute several parallel
 * requests, as different requests can lead to different cookies being set.
 * <p/>
 * A request that results in several redirects can occur in parallel with other requests. The only thing that should be
 * noted is that if Set-Cookie headers are received on parallel requests, all subsequent requests will carry these
 * cookies.
 *
 * <div class="note>
 * <div class="header">Note:</div>
 * Automatic redirection will not occur for requests other than GET or HEAD, as mandated by
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html">RFC2610's section 10</a>.
 * </div>
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHttpSession implements HttpSession, HandlerSessionFacade {

    // constants ------------------------------------------------------------------------------------------------------

    private static final int MAX_REDIRECTS = 3;

    // configuration --------------------------------------------------------------------------------------------------

    private int maxRedirects = 3;
    private String username;
    private String password;
    private String proxyHost;
    private int proxyPort;

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpClient client;
    private final HttpClient httpsClient;
    private final Lock headerReadLock;
    private final Lock headerWriteLock;
    private final List<Map.Entry<String, String>> headers;
    private final Map<Integer, ResponseCodeHandler> handlers;

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHttpSession(HttpClient client) {
        this(client, null);
    }

    public DefaultHttpSession(HttpClient client, HttpClient httpsClient) {
        if (client.isHttps()) {
            throw new IllegalArgumentException("HTTP client must not have SSL (HTTPS) support active");
        }

        if ((httpsClient != null) && !httpsClient.isHttps()) {
            throw new IllegalArgumentException("HTTPS client must have SSL (HTTPS) support active");
        }

        this.maxRedirects = MAX_REDIRECTS;
        this.client = client;
        this.httpsClient = httpsClient;
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.headerReadLock = rwLock.readLock();
        this.headerWriteLock = rwLock.writeLock();
        this.headers = new ArrayList<Map.Entry<String, String>>();

        this.handlers = new ConcurrentHashMap<Integer, ResponseCodeHandler>();
        this.addHandler(new AuthenticationResponseHandler());
        this.addHandler(new RedirectResponseHandler());
    }

    @Override
    public HttpRequestFuture<Void> execute(String path, HttpVersion version, HttpMethod method)
            throws CannotExecuteRequestException {
        return this.internalExecute(path, new DefaultHttpRequest(version, method, path), null);
    }

    @Override
    public <T> HttpRequestFuture<T> execute(String path, HttpVersion version, HttpMethod method,
                                            HttpResponseProcessor<T> responseProcessor)
            throws CannotExecuteRequestException {
        return this.internalExecute(path, new DefaultHttpRequest(version, method, path), responseProcessor);
    }

    @Override
    public <T> HttpRequestFuture<T> execute(String path, HttpRequest request) throws CannotExecuteRequestException {
        return this.internalExecute(path, request, null);
    }

    @Override
    public <T> HttpRequestFuture<T> execute(String path, HttpRequest request,
                                            HttpResponseProcessor<T> responseProcessor)
            throws CannotExecuteRequestException {
        return this.internalExecute(path, request, responseProcessor);
    }

    @Override
    public <T> HttpRequestFuture<T> execute(final HostPortAndUri target, final HttpRequestFuture<T> initialFuture,
                                            final HttpRequest request, final HttpResponseProcessor<T> responseProcessor)
            throws CannotExecuteRequestException {

        if (target.isHttps() && (this.httpsClient == null)) {
            throw new CannotExecuteRequestException("No HTTPS client was configured, cannot handle https:// requests");
        }

        final RecursiveAwareHttpRequest internalRequest;
        if (request instanceof RecursiveAwareHttpRequest) {
            internalRequest = ((RecursiveAwareHttpRequest) request);
            if (internalRequest.incrementRedirects() > this.maxRedirects) {
                throw new CannotExecuteRequestException("Max redirects hit (" + this.maxRedirects +")");
            }
        } else {
            internalRequest = new RecursiveAwareHttpRequest(request);
        }

        this.headerReadLock.lock();
        try {
            for (Map.Entry<String, String> header : this.headers) {
                internalRequest.addHeader(header.getKey(), header.getValue());
            }
        } finally {
            this.headerReadLock.unlock();
        }

        final HttpRequestFuture<T> internalFuture = new DefaultHttpRequestFuture<T>(true);
        if (this.proxyHost != null) {
            request.setUri(target.asUrl());
        } else {
            request.setUri(target.getUri());
        }

        if (request.getProtocolVersion() == HttpVersion.HTTP_1_1) {
            request.setHeader(HttpHeaders.Names.HOST, target.asHostAndPort());
        }

        HttpResponseProcessor<T> processor =
                (responseProcessor == null ? new TypedDiscardProcessor<T>() : responseProcessor);

        HttpClient client;
        if (target.isHttps()) {
            client = this.httpsClient;
        } else {
            client = this.client;
        }

        String host;
        int port;
        if (this.proxyHost != null) {
            host = this.proxyHost;
            port = this.proxyPort;
        } else {
            host = target.getHost();
            port = target.getPort();
        }

        // If there is no initial future, then its the first request.
        // Otherwise it's a subsequent request, generated either by auth or redirects.
        HttpRequestFuture<T> initialOrInternalFuture = initialFuture == null ? internalFuture : initialFuture;
        client.execute(host, port, request, processor)
                .addListener(new HttpSessionFutureListener<T>(this, initialOrInternalFuture, target,
                                                              internalRequest, processor));

        return internalFuture;
    }

    @Override
    public void addHeader(String headerName, String headerValue) {
        this.headerWriteLock.lock();
        try {
            // Avoid adding duplicate headers... for that we have to traverse all currently set headers...
            for (Map.Entry<String, String> header : this.headers) {
                if (header.getKey().equals(headerName) && header.getValue().equals(headerValue)) {
                    // Already exists, skip.
                    return;
                }
            }

            this.headers.add(new AbstractMap.SimpleEntry<String, String>(headerName, headerValue));
        } finally {
            this.headerWriteLock.unlock();
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue) {
        this.headerWriteLock.lock();
        try {
            Iterator<Map.Entry<String, String>> it = this.headers.iterator();
            boolean replaced = false;
            while (it.hasNext()) {
                Map.Entry<String, String> header = it.next();
                // If an existing header with the same name exists, replace it and delete all others.
                if (header.getKey().equals(headerName)) {
                    if (!replaced) {
                        header.setValue(headerValue);
                        replaced = true;
                    } else {
                        it.remove();
                    }
                }
            }
        } finally {
            this.headerWriteLock.unlock();
        }
    }

    @Override
    public void removeHeaders(String headerName) {
        this.headerWriteLock.lock();
        try {
            Iterator<Map.Entry<String, String>> it = this.headers.iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                if (entry.getKey().equals(headerName)) {
                    it.remove();
                }
            }
        } finally {
            this.headerWriteLock.unlock();
        }
    }

    @Override
    public Collection<Map.Entry<String, String>> getHeaders() {
        return Collections.unmodifiableCollection(this.headers);
    }

    @Override
    public void addHandler(ResponseCodeHandler handler) {
        for (int i : handler.handlesResponseCodes()) {
            this.handlers.put(i, handler);
        }
    }

    @Override
    public void removeHandler(int[] codes) {
        for (int code : codes) {
            this.handlers.remove(code);
        }
    }

    @Override
    public void removeHandler(ResponseCodeHandler handler) {
        for (int i : handler.handlesResponseCodes()) {
            this.handlers.remove(i);
        }
    }

    @Override
    public ResponseCodeHandler getHandler(int code) {
        return this.handlers.get(code);
    }

    @Override
    public void setProxy(String host, int port) {
        if ((port <= 0) || (port >= 65536)) {
            throw new IllegalArgumentException("Port must be in range 1-65535");
        }
        this.proxyHost = host;
        this.proxyPort = port;
    }

    @Override
    public String getProxyHost() {
        return this.proxyHost;
    }

    @Override
    public int getProxyPort() {
        return this.proxyPort;
    }

    @Override
    public void setAuthCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private <T> HttpRequestFuture<T> internalExecute(String path, HttpRequest request,
                                                     HttpResponseProcessor<T> processor)
            throws CannotExecuteRequestException {

        HostPortAndUri hostPortAndUri = HostPortAndUri.splitUrl(path);
        if (hostPortAndUri == null) {
            throw new CannotExecuteRequestException("Invalid URL provided: " + path);
        }

        return this.execute(hostPortAndUri, null, request, processor);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    /**
     * Returns the number of maximum redirects allowed.
     *
     * @return Currently configured number of max redirects allowed.
     */
    public int getMaxRedirects() {
        return maxRedirects;
    }

    /**
     * Sets the maximum number of redirects one single request is allowed to perform.
     * <p/>
     * If a request yields more than this number of redirects, the future will be released without the request ever
     * hitting a 200 or 400+ response.
     *
     * @param maxRedirects Max number of redirects allowed.
     */
    public void setMaxRedirects(int maxRedirects) {
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("MaxRedirects min value is 0");
        }
        this.maxRedirects = maxRedirects;
    }
}
