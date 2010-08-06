package org.factor45.hotpotato.client;

import org.factor45.hotpotato.client.connection.HttpConnection;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Helper class to hold both active connections and the number of connections opening to a given host.
 *
 * This class is not thread-safe and should only be updated by a thread at a time unless manual external synchronisation
 * is used.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class ConnectionPool {

    // internal vars --------------------------------------------------------------------------------------------------

    private boolean connectionFailures;
    private int connectionsOpening;
    private final Collection<HttpConnection> connections;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionPool() {
        this.connectionsOpening = 0;
        this.connections = new LinkedList<HttpConnection>();
    }

    // public methods -------------------------------------------------------------------------------------------------

    public void connectionOpening() {
        this.connectionsOpening++;
    }

    public void connectionFailed() {
        // Activate connection failure flag.
        this.connectionFailures = true;
        // Decrease opening connections indicator.
        this.connectionsOpening--;
    }

    public void connectionOpen(HttpConnection connection) {
        // Decrease opening connections indicator.
        if (this.connectionsOpening > 0) {
            this.connectionsOpening--;
        }
        // Reset connection failures.
        this.connectionFailures = false;
        // Add to pool.
        this.connections.add(connection);
    }

    public void connectionClosed(HttpConnection connection) {
        this.connections.remove(connection);
    }

    /**
     * Returns the number of both active and opening connections.
     *
     * @return The number of active connections + the number of pending connections.
     */
    public int getTotalConnections() {
        return this.connections.size() + this.connectionsOpening;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean hasConnectionFailures() {
        return connectionFailures;
    }

    public int getConnectionsOpening() {
        return connectionsOpening;
    }

    public Collection<HttpConnection> getConnections() {
        return connections;
    }
}
