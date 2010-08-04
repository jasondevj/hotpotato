package org.factor45.hotpotato.client.timeout;

import org.factor45.hotpotato.client.HttpRequestContext;

/**
 * Facility to manage timeouts for requests.
 *
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public interface TimeoutManager {

    boolean init();

    void terminate();

    /**
     * Manage the timeout for the provided context.
     *
     * @param context The request context to monitor. Timeout value is extracted from {@link
     *                org.factor45.hotpotato.client.HttpRequestContext#getTimeout()}.
     */
    void manageRequestTimeout(HttpRequestContext context);
}
