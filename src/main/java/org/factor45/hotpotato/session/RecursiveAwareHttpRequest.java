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

import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:bruno.carvalho@wit-software.com">Bruno de Carvalho</a>
 */
public class RecursiveAwareHttpRequest extends DefaultHttpRequest {

    // internal vars --------------------------------------------------------------------------------------------------

    private final AtomicInteger redirects;
    private boolean failedAuth;

    // constructors ---------------------------------------------------------------------------------------------------

    public RecursiveAwareHttpRequest(HttpRequest request) {
        this(request, 0);
    }

    public RecursiveAwareHttpRequest(HttpRequest request, int redirects) {
        super(request.getProtocolVersion(), request.getMethod(), request.getUri());
        for (Map.Entry<String, String> header : request.getHeaders()) {
            this.addHeader(header.getKey(), header.getValue());
        }
        this.setChunked(request.isChunked());
        this.setContent(request.getContent());
        this.redirects = new AtomicInteger(redirects);
    }

    public RecursiveAwareHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        this(httpVersion, method, uri, 0);
    }

    public RecursiveAwareHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, int redirects) {
        super(httpVersion, method, uri);
        this.redirects = new AtomicInteger(redirects);
    }

    // public methods -------------------------------------------------------------------------------------------------

    public int incrementRedirects() {
        return this.redirects.incrementAndGet();
    }

    public void failedAuth() {
        this.failedAuth = true;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public int getRedirects() {
        return redirects.get();
    }

    public boolean isFailedAuth() {
        return failedAuth;
    }
}
