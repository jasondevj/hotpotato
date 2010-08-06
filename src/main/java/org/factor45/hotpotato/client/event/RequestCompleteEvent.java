package org.factor45.hotpotato.client.event;

import org.factor45.hotpotato.client.HttpRequestContext;

/**
 * Event generated when a request completes, either successfully or not.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class RequestCompleteEvent implements HttpClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpRequestContext context;

    // constructors ---------------------------------------------------------------------------------------------------

    public RequestCompleteEvent(HttpRequestContext context) {
        this.context = context;
    }

    // HttpClientEvent ------------------------------------------------------------------------------------------------

    @Override
    public EventType getEventType() {
        return EventType.REQUEST_COMPLETE;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public HttpRequestContext getContext() {
        return this.context;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder().append("RequestCompleteEvent{").append(this.context).append('}').toString();
    }
}
