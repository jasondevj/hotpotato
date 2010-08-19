package org.factor45.hotpotato.speedtest;

import org.factor45.hotpotato.client.EventProcessorStatsProvider;
import org.factor45.hotpotato.client.HttpClient;
import org.factor45.hotpotato.client.factory.DefaultHttpClientFactory;
import org.factor45.hotpotato.client.factory.HttpClientFactory;
import org.factor45.hotpotato.client.event.EventType;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.response.BodyAsStringProcessor;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class PostBenchmark {

    // configuration --------------------------------------------------------------------------------------------------

    private final String host;
    private final int port;
    private final HttpRequest request;
    private final int requests;
    private final int dataGenInterval;
    private HttpClientFactory factory;

    // internal vars --------------------------------------------------------------------------------------------------

    private final List<float[]> existenceAndExecutionTimes;
    private final List<float[]> clientStats;
    private final List<float[]> throughputs;

    // constructors ---------------------------------------------------------------------------------------------------

    public PostBenchmark(String host, int port, HttpRequest request, int requests, int dataGenInterval) {
        this.host = host;
        this.port = port;
        this.request = request;
        this.requests = requests;
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

        List<HttpRequestFuture<?>> futures = new ArrayList<HttpRequestFuture<?>>(this.requests);
        HttpClient client = this.factory.getClient();
        if (!client.init()) {
            System.err.println("Failed to initialise HttpClient " + client);
            return;
        }

        long start = System.nanoTime();
        for (int i = 0; i < this.requests; i++) {
            futures.add(client.execute(this.host, this.port, 0, this.request, new BodyAsStringProcessor()));
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
                future.getCause().printStackTrace();
            } else {
                successfulRequests++;
                totalExecution += future.getExecutionTime();
                totalExistence += future.getExistenceTime();
                //System.out.println(future.getProcessedResult());
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

    public int getRequests() {
        return requests;
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

    public List<float[]> getExistenceAndExecutionTimes() {
        return existenceAndExecutionTimes;
    }

    public List<float[]> getClientStats() {
        return clientStats;
    }

    public List<float[]> getThroughputs() {
        return throughputs;
    }

    // main -----------------------------------------------------------------------------------------------------------

    public static void main(String[] args) {
        String host = "192.168.1.182";
        //String host = "localhost";
        int port = 8081;
        int repetitions = 1000;
        int dataGenInterval = 0;
        int iterations = 100;

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST,
                                                     "/collector/SubmitReport");

        String filename = "/media/windows/Users/bruno/Desktop/RMPTest.zip";
        FileInputStream fileInputStream = null;
        byte[] fileBytes;
        try {
            File file = new File(filename);
            if (file.length() > Integer.MAX_VALUE) {
                System.err.println("File '" + filename + "' is too large; max size is " + Integer.MAX_VALUE + " b.");
                return;
            }
            fileInputStream = new FileInputStream(file);
            fileBytes = new byte[(int) file.length()];
            int readBytes = fileInputStream.read(fileBytes);
            if (readBytes < file.length()) {
                System.err.println("Failed to read '" + filename + "' completely; expecting " +
                                   file.length() + ", read " + readBytes + ".");
                return;
            }
        } catch (FileNotFoundException e) {
            System.err.println("Could not locate file '" + filename + "'.");
            return;
        } catch (IOException e) {
            System.err.println("Error occured while reading file '" + filename + "'.");
            e.printStackTrace();
            return;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    System.err.println("Failed to close file input stream for file '" + filename + "'.");
                }
            }
        }

        System.err.println("Successfully read '" + filename + "'.");

        request.setContent(ChannelBuffers.copiedBuffer(fileBytes));
        request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, fileBytes.length);
        request.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/x-zip-compressed");
        PostBenchmark test = new PostBenchmark(host, port, request, repetitions, dataGenInterval);

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        factory.setGatherEventHandlingStats(true);
        factory.setMaxConnectionsPerHost(50);
        test.setFactory(factory);

        // warmup run...
        test.run();

        // real test
        for (int i = 0; i < iterations; i++) {
            test.run();
            System.out.println("Iteration " + i + " done.");
        }

        System.out.println("avg execution\tavg existence");
        for (float[] time : test.getExistenceAndExecutionTimes()) {
            System.out.println(time[0] + "\t" + time[1]);
        }
        System.out.println("Total\texecute\texecute %\tcomplete\tcomplete %\topen\topen %\tclosed\tclosed %\tevent #");
        for (float[] stat : test.getClientStats()) {
            System.out.println(stat[0] + "\t" + stat[1] + "\t" + stat[2] + "\t" + stat[3] + "\t" + stat[4] + "\t" +
                               stat[5] + "\t" + stat[6] + "\t" + stat[7] + "\t" + stat[8] + "\t" + stat[9]);
        }
        System.out.println("Test time\tSuccessful requests\tThroughput");
        for (float[] throughput : test.getThroughputs()) {
            System.out.println(throughput[0] + "\t" + throughput[1] + "\t" + (throughput[2] * 1000));
        }
    }
}
