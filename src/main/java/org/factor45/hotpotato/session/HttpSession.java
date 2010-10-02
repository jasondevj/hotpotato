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

package org.factor45.hotpotato.session;

import org.factor45.hotpotato.client.CannotExecuteRequestException;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.response.HttpResponseProcessor;
import org.factor45.hotpotato.session.handler.ResponseCodeHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.Collection;
import java.util.Map;

/**
 * A simpler and friendlier interface to execute HTTP requests.
 * <p/>
 * The {@code HttpSession} interface aims to simplify the usage of a {@link org.factor45.hotpotato.client.HttpClient} by
 * allowing requests to be passed as a full URL (e.g. http://hotpotato.factor45.org) rather than split by host, port
 * and URI (like {@link org.factor45.hotpotato.client.HttpClient} does.
 * <p/>
 * It also offers other handy utilities like automated redirect, authorization and proxy handling for both HTTP 1.0
 * and HTTP 1.1.
 *
 * <h2>Relation to HttpClient</h2>
 * A {@code HttpSession} can be seen as a context in which several requests are executed, such as a session for a given
 * user account to a site or even as a browser tab.
 * <p/>
 * The {@code HttpSession} relies on a underlying {@link org.factor45.hotpotato.client.HttpClient} to manage the request
 * execution itself, so you will still need to configure and provide this implementation when creating a new session.
 * <p/>
 * Thus, the focus of implementations of this class is merely to automate some of the most boring tasks such as
 * decomposing URLs in the format 'http://host:port/uri' to something that
 * {@link org.factor45.hotpotato.client.HttpClient} likes, as well as adding necessary headers when sending request in
 * HTTP 1.1 or even when going through a proxy. Other repetitive tasks can also be automated using this interface.
 *
 * <h2>Automating response behaviors</h2>
 * Automated response handling can be achieved by plugging in {@link ResponseCodeHandler}s. These handlers intercept
 * responses and perform either some computation or another subsequent request (like authorization or redirect
 * handling).
 * <p/>
 * For more information, check the documentation for {@link ResponseCodeHandler}.
 *
 * <h2>Automated cookie storage</h2>
 * By default, no cookies are stored, leaving it to the programmer to manually handle cookie management. If you want to
 * store all cookies received by requests executed using a specific session, you have to manually add a
 * {@link org.factor45.hotpotato.session.handler.CookieStoringResponseHandler} using the method
 * {@link #addHandler(ResponseCodeHandler)}.
 * <p/>
 * Example:
 * <pre>
 * HttpSession session = ...;
 * session.addHandler(new CookieStoringResponseHandler());
 * </pre>
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 *
 * @see org.factor45.hotpotato.session.handler.ResponseCodeHandler
 * @see org.factor45.hotpotato.client.HttpClient
 */
public interface HttpSession {

    /**
     * Execute a HTTP request.
     *
     * @param path Target URL.
     * @param version Version of the protocol to be used.
     * @param method Method to be used.
     *
     * @return The {@link HttpRequestFuture} associated with this request.
     *
     * @throws CannotExecuteRequestException Thrown when something goes wrong inside the underlying
     *                                       {@link org.factor45.hotpotato.client.HttpClient}.
     */
    HttpRequestFuture<Void> execute(String path, HttpVersion version, HttpMethod method)
            throws CannotExecuteRequestException;

    /**
     * Execute a HTTP request.
     *
     * @param path Target URL.
     * @param version Version of the protocol to be used.
     * @param method Method to be used.
     * @param responseProcessor The response processor for this request.
     *
     * @return The {@link HttpRequestFuture} associated with this request.
     *
     * @throws CannotExecuteRequestException Thrown when something goes wrong inside the underlying
     *                                       {@link org.factor45.hotpotato.client.HttpClient}.
     */
    <T> HttpRequestFuture<T> execute(String path, HttpVersion version, HttpMethod method,
                                     HttpResponseProcessor<T> responseProcessor)
            throws CannotExecuteRequestException;

    /**
     * Execute a HTTP request.
     *
     * @param path Target URL.
     * @param request {@link HttpRequest} object that will be used.
     *
     * @return The {@link HttpRequestFuture} associated with this request.
     *
     * @throws CannotExecuteRequestException Thrown when something goes wrong inside the underlying
     *                                       {@link org.factor45.hotpotato.client.HttpClient}.
     */
    <T> HttpRequestFuture<T> execute(String path, HttpRequest request)
            throws CannotExecuteRequestException;

    /**
     * Execute a HTTP request.
     *
     * @param path Target URL.
     * @param request {@link HttpRequest} object that will be used.
     * @param responseProcessor The response processor for this request.
     *
     * @return The {@link HttpRequestFuture} associated with this request.
     *
     * @throws CannotExecuteRequestException Thrown when something goes wrong inside the underlying
     *                                       {@link org.factor45.hotpotato.client.HttpClient}.
     */
    <T> HttpRequestFuture<T> execute(String path, HttpRequest request, HttpResponseProcessor<T> responseProcessor)
            throws CannotExecuteRequestException;

    /**
     * Add a permanent header to this session, that will be set for each of the subsequent requests.
     * <p/>
     * Duplicate headers names are allowed (and not verified) so use this with caution.
     * Duplicate headers (equal name and value) will be ignored.
     *
     * @param headerName Name of the header.
     * @param headerValue Value of the header.
     */
    void addHeader(String headerName, String headerValue);

    /**
     * Delete all headers with the same name and add a new header.
     * <p/>
     * Unlike {@link #addHeader(String, String)}, this method replaces the existing header (if more than one under the
     * same name exists, they're all deleted) with the newly provided one.
     *
     * @param headerName Name of the header.
     * @param headerValue Value of the header.
     */
    void setHeader(String headerName, String headerValue);

    /**
     * Deletes all headers that match the given name.
     *
     * @param headerName Name of the header to delete.
     */
    void removeHeaders(String headerName);

    /**
     * Returns a list of name:value pairs for all the headers manually set (or automatically set via
     * {@link ResponseCodeHandler}).
     *
     * @return List of headers.
     */
    Collection<Map.Entry<String, String>> getHeaders();

    /**
     * Adds a {@link ResponseCodeHandler} to the session.
     *
     * @param handler The handler to add
     */
    void addHandler(ResponseCodeHandler handler);

    /**
     * Removes all handlers that match the given response codes.
     *
     * @param codes Response code array.
     */
    void removeHandler(int[] codes);

    /**
     * Removes a handler by reference.
     *
     * @param handler Handler to be removed.
     */
    void removeHandler(ResponseCodeHandler handler);

    /**
     * Returns the handler that matches the given code.
     *
     * @param code HTTP numeric esponse code.
     *
     * @return The associated {@link ResponseCodeHandler} or {@code null} if none.
     */
    ResponseCodeHandler getHandler(int code);

    /**
     * Configures this session with the given host and port for HTTP proxying.
     *
     * @param host Host of the proxy.
     * @param port Port of the proxy.
     */
    void setProxy(String host, int port);

    /**
     * Returns the host of the proxy.
     *
     * @return Proxy host.
     */
    String getProxyHost();

    /**
     * Returns the proxy port.
     *
     * @return Proxy port.
     */
    int getProxyPort();

    /**
     * Configures this session with the given credentials.
     *
     * @param username Username.
     * @param password Password.
     */
    void setAuthCredentials(String username, String password);

    /**
     * Returns the configured username.
     *
     * @return Username.
     */
    String getUsername();

    /**
     * Returns the configured password.
     *
     * @return Password.
     */
    String getPassword();
}
