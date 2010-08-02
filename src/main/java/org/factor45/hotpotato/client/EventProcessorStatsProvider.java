package org.factor45.hotpotato.client;

import org.factor45.hotpotato.client.event.EventType;

/**
 * Provides statistics for an event processor.
 * <p/>
 * This is used mostly for development stages, to compare improvements of modifications.
 * <p/>
 * @see StatsGatheringHttpClient
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface EventProcessorStatsProvider {

    /**
     * @return Total execution time of the event processor, in milliseconds.
     */
    long getTotalExecutionTime();

    /**
     * @param event Type of event to query for.
     *
     * @return Processing time for events of given type, in milliseconds.
     */
    long getEventProcessingTime(EventType event);

    /**
     * @param event Type of event to query for.
     *
     * @return Processing percentage (ratio) of a given type, between 0 and 100
     */
    float getEventProcessingPercentage(EventType event);

    /**
     * @return Total of processed events.
     */
    long getProcessedEvents();
}
