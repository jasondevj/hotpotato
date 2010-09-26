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

package org.factor45.hotpotato.session.handler;

import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.response.HttpResponseProcessor;
import org.factor45.hotpotato.session.HandlerSessionFacade;
import org.factor45.hotpotato.session.HttpSession;
import org.factor45.hotpotato.session.RecursiveAwareHttpRequest;
import org.factor45.hotpotato.util.HostPortAndUri;

/**
 * Handles responses for a particular request and either performs some computation before marking it as a success or
 * executes another subrequest under the umbrella of the original request (e.g. authentication, redirection). 
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface ResponseCodeHandler {

    /**
     * List of response codes handler by this handler.
     *
     * @return List of HTTP response codes.
     */
    int[] handlesResponseCodes();

    /**
     * Performs some computation on the response or executes another request.
     *
     * @param session A facade for {@link HttpSession}.
     * @param originalFuture The original future created when the request was submitted to the {@link HttpSession}.
     * @param future The future of the last request.
     * @param target The target URL of the first request.
     * @param request The first request
     * @param processor The processor provided when the request was submitted to {@link HttpSession}.
     */
    <T> void handleResponse(HandlerSessionFacade session, HttpRequestFuture<T> originalFuture,
                            HttpRequestFuture<T> future, HostPortAndUri target, RecursiveAwareHttpRequest request,
                            HttpResponseProcessor<T> processor);
}
