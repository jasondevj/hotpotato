package org.factor45.hotpotato.client.event;

import org.factor45.hotpotato.client.connection.HttpConnection;

/**
 * Event generated when an attempt to connect fails.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class ConnectionFailedEvent implements HttpClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpConnection connection;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionFailedEvent(HttpConnection connection) {
        this.connection = connection;
    }

    // HttpClientEvent ------------------------------------------------------------------------------------------------

    @Override
    public EventType getEventType() {
        return EventType.CONNECTION_FAILED;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public HttpConnection getConnection() {
        return this.connection;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("ConnectionFailedEvent{")
                .append("connection=").append(this.connection)
                .append('}').toString();
    }
}
