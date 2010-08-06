package org.factor45.hotpotato.client;

import org.factor45.hotpotato.client.event.ConnectionClosedEvent;
import org.factor45.hotpotato.client.event.ConnectionFailedEvent;
import org.factor45.hotpotato.client.event.ConnectionOpenEvent;
import org.factor45.hotpotato.client.event.EventType;
import org.factor45.hotpotato.client.event.ExecuteRequestEvent;
import org.factor45.hotpotato.client.event.HttpClientEvent;
import org.factor45.hotpotato.client.event.RequestCompleteEvent;

/**
 * Statistics gathering version of {@link DefaultHttpClient}.
 * <p/>
 * Overrides the {@code eventHandlingLoop()} method (with the exact same code) but adds a couple of time measurement
 * calls. Even though these add neglectable overhead, for production scenarios where stats gathering is not vital, you
 * should use {@link DefaultHttpClient} rather than this one.
 * <p/>
 * This is only useful if you're implementing your own {@link org.factor45.hotpotato.client.host.HostContext} or {@link
 * org.factor45.hotpotato.client.connection.HttpConnection} and want to test the impact of your changes.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class StatsGatheringHttpClient extends AbstractHttpClient implements EventProcessorStatsProvider {

    // internal vars --------------------------------------------------------------------------------------------------

    protected long totalTime = 0;
    protected long executeRequestTime = 0;
    protected long requestCompleteTime = 0;
    protected long connectionOpenTime = 0;
    protected long connectionClosedTime = 0;
    protected long connectionFailedTime = 0;
    protected int events = 0;

    // AbstractHttpClient ---------------------------------------------------------------------------------------------

    @Override
    public void eventHandlingLoop() {
        for (; ;) {
            // Manual synchronisation here because before removing an element, we first need to check whether an
            // active available connection exists to satisfy the request.
            try {
                HttpClientEvent event = eventQueue.take();
                if (event == POISON) {
                    this.eventConsumerLatch.countDown();
                    return;
                }
                this.events++;
                long start = System.nanoTime();

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        this.handleExecuteRequest((ExecuteRequestEvent) event);
                        this.executeRequestTime += System.nanoTime() - start;
                        break;
                    case REQUEST_COMPLETE:
                        this.handleRequestComplete((RequestCompleteEvent) event);
                        this.requestCompleteTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_OPEN:
                        this.handleConnectionOpen((ConnectionOpenEvent) event);
                        this.connectionOpenTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_CLOSED:
                        this.handleConnectionClosed((ConnectionClosedEvent) event);
                        this.connectionClosedTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_FAILED:
                        this.handleConnectionFailed((ConnectionFailedEvent) event);
                        this.connectionFailedTime += System.nanoTime() - start;
                        break;
                    default:
                        // Consume and do nothing, unknown event.
                }
                this.totalTime += System.nanoTime() - start;
            } catch (InterruptedException e) {
                // ignore, poisoning the queue is the only way to stop
            }
        }

    }

    // EventProcessorStatsProvider ------------------------------------------------------------------------------------

    @Override
    public long getTotalExecutionTime() {
        return this.totalTime / 1000000;
    }

    @Override
    public long getEventProcessingTime(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return this.executeRequestTime / 1000000;
            case REQUEST_COMPLETE:
                return this.requestCompleteTime / 1000000;
            case CONNECTION_OPEN:
                return this.connectionOpenTime / 1000000;
            case CONNECTION_CLOSED:
                return this.connectionClosedTime / 1000000;
            case CONNECTION_FAILED:
                return this.connectionFailedTime / 1000000;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override
    public float getEventProcessingPercentage(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return (this.executeRequestTime / (float) this.totalTime) * 100;
            case REQUEST_COMPLETE:
                return (this.requestCompleteTime / (float) this.totalTime) * 100;
            case CONNECTION_OPEN:
                return (this.connectionOpenTime / (float) this.totalTime) * 100;
            case CONNECTION_CLOSED:
                return (this.connectionClosedTime / (float) this.totalTime) * 100;
            case CONNECTION_FAILED:
                return (this.connectionFailedTime / (float) this.totalTime) * 100;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override
    public long getProcessedEvents() {
        return this.events;
    }
}
