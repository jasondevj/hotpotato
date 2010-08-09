package org.factor45.hotpotato.client.host;

/**
 * The default and simplest implementation of the HostContext interface.
 *
 * This class is designed for extension as it contains boilerplate code that all implementations of HostContext would
 * surely have as well. Typically, an implementation would only override the drainQueue() strategy
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class DefaultHostContext extends AbstractHostContext {

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHostContext(String host, int port, int maxConnections) {
        super(host, port, maxConnections);
    }
}
