package org.factor45.hotpotato.client.event;

import org.factor45.hotpotato.client.connection.HttpConnection;

/**
 * Event generated when a new connection is successfully opened.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class ConnectionOpenEvent implements HttpClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpConnection connection;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionOpenEvent(HttpConnection connection) {
        this.connection = connection;
    }

    // HttpClientEvent ------------------------------------------------------------------------------------------------

    @Override
    public EventType getEventType() {
        return EventType.CONNECTION_OPEN;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public HttpConnection getConnection() {
        return this.connection;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder().append("ConnectionOpenEvent{").append(this.connection).append('}').toString();
    }
}
