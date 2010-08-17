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

package org.factor45.hotpotato.request.factory;

import org.factor45.hotpotato.request.ConcurrentHttpRequestFuture;
import org.factor45.hotpotato.request.HttpRequestFuture;

/**
 * @author <a href="mailto:bruno.carvalho@wit-software.com">Bruno de Carvalho</a>
 */
public class ConcurrentHttpRequestFutureFactory implements HttpRequestFutureFactory {

    // HttpRequestFuture ----------------------------------------------------------------------------------------------

    @Override
    public HttpRequestFuture getFuture() {
        return new ConcurrentHttpRequestFuture();
    }

    @Override
    public HttpRequestFuture getFuture(boolean cancellable) {
        return new ConcurrentHttpRequestFuture(cancellable);
    }
}
