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
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import com.biasedbit.hotpotato.util.HostPortAndUri;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Facade whose single purpose is to keep this method (needed by
 * {@link com.biasedbit.hotpotato.session.handler.ResponseCodeHandler} implementations) outside of {@link HttpSession}.
 *
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public interface HandlerSessionFacade extends HttpSession {

    <T> HttpRequestFuture<T> execute(HostPortAndUri target, HttpRequestFuture<T> initialFuture,
                                     HttpRequest request, HttpResponseProcessor<T> processor)
            throws CannotExecuteRequestException;
}
