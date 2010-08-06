package org.factor45.hotpotato.client.event;

import org.factor45.hotpotato.client.HttpRequestContext;

/**
 * Event generated when a new execution request is issued.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class ExecuteRequestEvent implements HttpClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpRequestContext context;

    // constructors ---------------------------------------------------------------------------------------------------

    public ExecuteRequestEvent(HttpRequestContext context) {
        this.context = context;
    }

    // HttpClientEvent ------------------------------------------------------------------------------------------------

    @Override
    public EventType getEventType() {
        return EventType.EXECUTE_REQUEST;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public HttpRequestContext getContext() {
        return this.context;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder().append("ExecuteRequestEvent{").append(this.context).append('}').toString();
    }
}
