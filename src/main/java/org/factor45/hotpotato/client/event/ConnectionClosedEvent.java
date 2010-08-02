package org.factor45.hotpotato.client.event;

import org.factor45.hotpotato.client.connection.HttpConnection;

/**
 * Event generated when an established connection is closed.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class ConnectionClosedEvent implements HttpClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpConnection connection;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionClosedEvent(HttpConnection connection) {
        this.connection = connection;
    }

    // HttpClientEvent ------------------------------------------------------------------------------------------------

    @Override
    public EventType getEventType() {
        return EventType.CONNECTION_CLOSED;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public HttpConnection getConnection() {
        return this.connection;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder().append("ConnectionClosedEvent{").append(this.connection).append('}').toString();
    }
}
