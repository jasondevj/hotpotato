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

package org.factor45.hotpotato.client.factory;

import org.factor45.hotpotato.client.HttpClient;

/**
 * Factory for {@link HttpClient} instances.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HttpClientFactory {

    /**
     * Creates, configures and returns an uninitialised {@link HttpClient} instance.
     * Always remember to call {@code init()} on the instance returned (and {@code terminate()} once you're done
     * with it).
     *
     * @return A newly configured uninitialised {@link HttpClient}.
     */
    HttpClient getClient();
}
