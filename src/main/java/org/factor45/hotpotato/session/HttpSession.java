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
import org.factor45.hotpotato.utils.HostPortAndUri;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * @author <a href="mailto:bruno.carvalho@wit-software.com">Bruno de Carvalho</a>
 */
public interface HttpSession {

    <T> HttpRequestFuture<T> execute(String path, HttpVersion version, HttpMethod method)
        throws CannotExecuteRequestException;

    <T> HttpRequestFuture<T> execute(String path, HttpVersion version, HttpMethod method,
                                     HttpResponseProcessor<T> responseProcessor)
        throws CannotExecuteRequestException;

    <T> HttpRequestFuture<T> execute(String path, HttpRequest request)
            throws CannotExecuteRequestException;

    <T> HttpRequestFuture<T> execute(String path, HttpRequest request, HttpResponseProcessor<T> responseProcessor)
            throws CannotExecuteRequestException;

    <T> HttpRequestFuture<T> execute(HostPortAndUri target, HttpRequest request, HttpResponseProcessor<T> processor)
        throws CannotExecuteRequestException;

    void addHeader(String headerName, String headerValue);

    void removeHeaders(String headerName);

    void addHandler(ResponseCodeHandler handler);

    void removeHandler(int[] codes);

    void removeHandler(ResponseCodeHandler handler);

    ResponseCodeHandler getHandler(int code);
}
