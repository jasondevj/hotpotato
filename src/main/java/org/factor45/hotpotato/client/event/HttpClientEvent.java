package org.factor45.hotpotato.client.event;

/**
 * An generic event that is consumed by the consumer thread in {@link org.factor45.hotpotato.client.AbstractHttpClient}.
 * <p/>
 * When an event is consumed it will generate actions, such as executing requests, opening connections, queueing
 * requests, etc.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HttpClientEvent {

    /**
     * @return Type of the event.
     */
    EventType getEventType();
}
