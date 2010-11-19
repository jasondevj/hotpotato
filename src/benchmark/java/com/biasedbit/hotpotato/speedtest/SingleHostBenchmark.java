package com.biasedbit.hotpotato.speedtest;

import com.biasedbit.hotpotato.client.EventProcessorStatsProvider;
import com.biasedbit.hotpotato.client.HttpClient;
import com.biasedbit.hotpotato.client.connection.factory.PipeliningHttpConnectionFactory;
import com.biasedbit.hotpotato.client.event.EventType;
import com.biasedbit.hotpotato.client.factory.DefaultHttpClientFactory;
import com.biasedbit.hotpotato.client.factory.HttpClientFactory;
import com.biasedbit.hotpotato.client.host.factory.EagerDrainHostContextFactory;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.DiscardProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class SingleHostBenchmark {

    // configuration --------------------------------------------------------------------------------------------------

    private final String host;
    private final int port;
    private final HttpRequest request;
    private final int repetitions;
    private final int dataGenInterval;
    private HttpClientFactory factory;

    // internal vars --------------------------------------------------------------------------------------------------

    private final List<float[]> existenceAndExecutionTimes;
    private final List<float[]> clientStats;
    private final List<float[]> throughputs;

    // constructors ---------------------------------------------------------------------------------------------------

    public SingleHostBenchmark(String host, int port, HttpRequest request, int repetitions,
                               int dataGenInterval) {
        this.host = host;
        this.port = port;
        this.request = request;
        this.repetitions = repetitions;
        this.dataGenInterval = dataGenInterval;

        this.existenceAndExecutionTimes = new ArrayList<float[]>();
        this.clientStats = new ArrayList<float[]>();
        this.throughputs = new ArrayList<float[]>();
    }

    // public methods -------------------------------------------------------------------------------------------------

    public void run() {
        if (this.factory == null) {
            this.factory = new DefaultHttpClientFactory();
            ((DefaultHttpClientFactory) this.factory).setGatherEventHandlingStats(true);
        }

        List<HttpRequestFuture<?>> futures = new ArrayList<HttpRequestFuture<?>>(this.repetitions);
        HttpClient client = this.factory.getClient();
        if (!client.init()) {
            System.err.println("Failed to initialise HttpClient " + client);
            return;
        }

        long start = System.nanoTime();
        for (int i = 0; i < this.repetitions; i++) {
            futures.add(client.execute(this.host, this.port, 300, this.request, new DiscardProcessor()));
            if (this.dataGenInterval > 0) {
                try {
                    Thread.sleep(this.dataGenInterval);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }

        long successfulRequests = 0;
        long totalExistence = 0;
        long totalExecution = 0;
        for (HttpRequestFuture<?> future : futures) {
            future.awaitUninterruptibly();
            if (!future.isSuccess()) {
                System.err.println(future.getCause());
            } else {
                successfulRequests++;
                totalExecution += future.getExecutionTime();
                totalExistence += future.getExistenceTime();
            }
        }
        long total = System.nanoTime() - start;
        this.throughputs.add(new float[]{total / 1000000, successfulRequests, (successfulRequests / (float) total)});

        // average execution time - average existence time
        this.existenceAndExecutionTimes.add(new float[]{(totalExecution / (float) successfulRequests),
                                                        (totalExistence / (float) successfulRequests)});
        if (client instanceof EventProcessorStatsProvider) {
            EventProcessorStatsProvider stats = (EventProcessorStatsProvider) client;
            // total execution time - execute - execute % - complete - complete % - open - open % - closed - closed %
            this.clientStats.add(new float[]{
                    stats.getTotalExecutionTime(),
                    stats.getEventProcessingTime(EventType.EXECUTE_REQUEST),
                    stats.getEventProcessingPercentage(EventType.EXECUTE_REQUEST),
                    stats.getEventProcessingTime(EventType.REQUEST_COMPLETE),
                    stats.getEventProcessingPercentage(EventType.REQUEST_COMPLETE),
                    stats.getEventProcessingTime(EventType.CONNECTION_OPEN),
                    stats.getEventProcessingPercentage(EventType.CONNECTION_OPEN),
                    stats.getEventProcessingTime(EventType.CONNECTION_CLOSED),
                    stats.getEventProcessingPercentage(EventType.CONNECTION_CLOSED),
                    stats.getProcessedEvents()
            });
        }

        client.terminate();
    }

    public void clearStats() {
        this.clientStats.clear();
        this.existenceAndExecutionTimes.clear();
        this.throughputs.clear();
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public int getDataGenInterval() {
        return dataGenInterval;
    }

    public HttpClientFactory getFactory() {
        return factory;
    }

    public void setFactory(HttpClientFactory factory) {
        this.factory = factory;
    }

    public List<float[]> getExecutionAndExistenceTimes() {
        return existenceAndExecutionTimes;
    }

    public List<float[]> getClientStats() {
        return clientStats;
    }

    public List<float[]> getThroughputs() {
        return throughputs;
    }

    // main -----------------------------------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        //String host = "10.0.0.2";
        HttpVersion version = HttpVersion.HTTP_1_1;
        int port = 8080;
        int repetitions = 1000;
        int dataGenInterval = 0;
        int connectionsPerHost = 10;
        boolean useNio = true;
        int iterations = 100;

        HttpRequest request = new DefaultHttpRequest(version, HttpMethod.GET, "/");

        SingleHostBenchmark test = new SingleHostBenchmark(host, port, request, repetitions, dataGenInterval);

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        //factory.setDebug(true);
        factory.setGatherEventHandlingStats(true);
        factory.setMaxConnectionsPerHost(connectionsPerHost);
        factory.setUseNio(useNio);
        factory.setConnectionFactory(new PipeliningHttpConnectionFactory());
        factory.setHostContextFactory(new EagerDrainHostContextFactory());
        //factory.setFutureFactory(new ConcurrentHttpRequestFutureFactory());
        factory.setConnectionTimeoutInMillis(20000);
        test.setFactory(factory);

        // warmup run...
        test.run();
        test.clearStats();

        // real test
        for (int i = 0; i < iterations; i++) {
            System.out.println("Iteration " + i + " started.");
            test.run();
        }

        float averageExistence = 0;
        float averageExecution = 0;
        System.out.println("avg execution\tavg existence");
        for (float[] time : test.getExecutionAndExistenceTimes()) {
            System.out.println(time[0] + "\t" + time[1]);
            averageExecution += time[0];
            averageExistence += time[1];
        }
        System.out.println("Total\texecute\texecute %\tcomplete\tcomplete %\topen\topen %\tclosed\tclosed %\tevent #");
        for (float[] stat : test.getClientStats()) {
            System.out.println(stat[0] + "\t" + stat[1] + "\t" + stat[2] + "\t" + stat[3] + "\t" + stat[4] + "\t" +
                               stat[5] + "\t" + stat[6] + "\t" + stat[7] + "\t" + stat[8] + "\t" + stat[9]);
        }
        float averageTestTime = 0;
        float averageThroughput = 0;
        System.out.println("Test time\tSuccessful requests\tThroughput");
        for (float[] throughput : test.getThroughputs()) {
            averageTestTime += throughput[0];
            averageThroughput += throughput[2] * 1000000000;
            System.out.println(throughput[0] + "\t" + throughput[1] + "\t" + (throughput[2] * 1000000000));
        }

        System.out.println();
        System.out.println(iterations + " batches of " + repetitions + " " + version + " GET " +
                          (dataGenInterval == 0 ? "in burst mode" : "with data generation at a steady " +
                                                                    dataGenInterval + "ms interval") +
                          " using " + connectionsPerHost + " connections per host and " + (useNio ? "NIO" : "OIO"));
        System.out.println("- Batch averages -----------------------------------------");
        System.out.println("Average execution time p/ request: " + (averageExecution / iterations) + "ms");
        System.out.println("Average existence of each request: " + (averageExistence / iterations) + "ms");
        System.out.println("Average batch test time:           " + (averageTestTime / iterations) + "ms");
        System.out.println("Average throughput (req/sec):      " + (averageThroughput / iterations) + "req/s");
    }
}
