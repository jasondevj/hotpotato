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
