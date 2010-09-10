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

import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.request.HttpRequestFutureListener;
import org.factor45.hotpotato.response.HttpResponseProcessor;
import org.factor45.hotpotato.session.handler.ResponseCodeHandler;
import org.factor45.hotpotato.util.HostPortAndUri;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import java.util.List;

/**
* @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
*/
public class HttpSessionFutureListener<T> implements HttpRequestFutureListener<T> {

    // internal vars ----------------------------------------------------------------------------------------------

    private final HttpSession session;
    private final HttpRequestFuture<T> initialFuture;
    private final HostPortAndUri target;
    private final RecursiveAwareHttpRequest request;
    private final HttpResponseProcessor<T> processor;

    // constructors -----------------------------------------------------------------------------------------------

    public HttpSessionFutureListener(HttpSession session, HttpRequestFuture<T> initialFuture,
                                     HostPortAndUri target, RecursiveAwareHttpRequest request,
                                     HttpResponseProcessor<T> processor) {
        this.session = session;
        this.initialFuture = initialFuture;
        this.target = target;
        this.request = request;
        this.processor = processor;
    }

    // HttpRequestFutureListener ----------------------------------------------------------------------------------

    @Override
    public void operationComplete(HttpRequestFuture<T> future) throws Exception {
        if (future.isSuccess()) {
            List<String> cookies = future.getResponse().getHeaders(HttpHeaders.Names.SET_COOKIE);
            if ((cookies != null) && !cookies.isEmpty()) {
                for (String cookie : cookies) {
                    this.session.addHeader(HttpHeaders.Names.COOKIE, cookie);
                }
            }

            ResponseCodeHandler handler = this.session.getHandler(future.getResponseStatusCode());
            if (handler == null) {
                // Defaults to simply setting the response and processed result
                this.initialFuture.setSuccess(future.getProcessedResult(), future.getResponse());
            } else {
                handler.handleResponse(this.session, this.initialFuture, future, this.target,
                                       this.request, this.processor);
            }
        } else if (!future.isCancelled()) {
            this.initialFuture.setFailure(future.getCause());
        }
    }
}
