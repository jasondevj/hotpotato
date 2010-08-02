package org.factor45.hotpotato.client.event;

/**
 * Definition of possible event types for {@link org.factor45.hotpotato.client.AbstractHttpClient}'s consumer thread.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public enum EventType {

    /**
     * A new request execution call was issued.
     */
    EXECUTE_REQUEST,
    /**
     * A request execution was completeed (successfully or not).
     */
    REQUEST_COMPLETE,
    /**
     * A new connection to a given host was opened.
     */
    CONNECTION_OPEN,
    /**
     * An existing connection to a host was closed.
     */
    CONNECTION_CLOSED,
    /**
     * An attempt to connect to a given host failed.
     */
    CONNECTION_FAILED,
}
