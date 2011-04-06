package com.biasedbit.hotpotato.client.manager;

import com.biasedbit.hotpotato.client.HttpClient;
import com.biasedbit.hotpotato.client.factory.HttpClientFactory;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.DiscardProcessor;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DefaultHttpClientManager works as a wrapper for HttpClient so that when using the client only the request need to
 * be set and other parameters will be set throe dependency injection. This Manager also can be used to do the
 * load balancing between multiple clients and fail-over
 */
public class DefaultHttpClientManager implements HttpClientManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHttpClientManager.class);
    private HttpClientFactory httpClientFactory;
    private HttpResponseProcessor httpResponseProcessor;
    private URI hostUri;
    private HttpClient defaultHttpClient;

    /**
     * This will decide the number of clients that can be used. If the number is set more than one client will be used to send the request
     */
    private int numberOfClients = 5;
    private RoundRobinLogic roundRobinLogic;


    public void init() {
        logger.info("Initiating Client Manager with client pool of [" + numberOfClients + "]");
        this.defaultHttpClient = httpClientFactory.getClient();
        defaultHttpClient.init();
        logger.info("Default client initiated");
        final ArrayList<HttpClient> httpClients = new ArrayList<HttpClient>(numberOfClients);
        HttpClient client;
        for (int i = 0; i < numberOfClients; i++) {
            client = httpClientFactory.getClient();
            client.init();
            httpClients.add(client);
        }
        roundRobinLogic = new RoundRobinLogic(httpClients);
        logger.info("Client manager successfully initiated...");
    }

    /**
     * Execute the request, before executing the Host URI must be set and if a HttpResponseProcessor is not set
     * the Response will be discarded.
     *
     * @param request request which was is suppose to be sent.
     * @return
     */
    @Override
    public HttpRequestFuture execute(HttpRequest request) {
        validateHostUri();
        if (httpResponseProcessor == null) {
            return execute(hostUri.getHost(), hostUri.getPort(), request,
                    DiscardProcessor.getInstance());
        } else {
            return execute(hostUri.getHost(), hostUri.getPort(), request, httpResponseProcessor);
        }
    }

    private void validateHostUri() {
        if (hostUri == null) {
            throw new RuntimeException("Default URI must be given to use this method or URI must be passed as a parameter");
        }
    }

    @Override
    public HttpRequestFuture execute(HttpRequest request, HttpResponseProcessor httpResponseProcessor) {
        validateHostUri();
        return execute(hostUri.getHost(), hostUri.getPort(), request, httpResponseProcessor);
    }

    @Override
    public HttpRequestFuture execute(String host, int port, HttpRequest request, HttpResponseProcessor httpResponseProcessor) {
        HttpClient client = null;
        try {
            client = roundRobinLogic.nextClient();
        } catch (Throwable e) {
            logger.error("Error finding the next client : ", e);
        }
        if (client != null) {
            return client.execute(host, port, request, httpResponseProcessor);
        } else {
            logger.warn("Error getting the next client, using default client for execution");
            return defaultHttpClient.execute(host, port, request, httpResponseProcessor);
        }
    }

    /**
     * Return the HttpClient which was created initially.
     *
     * @return Client Which was created initially.
     */
    @Override
    public HttpClient getDefaultHttpClient() {
        return defaultHttpClient;
    }

    /**
     * Creates a new Client from the factory and returns it.
     *
     * @return New Client.
     */
    @Override
    public HttpClient createNewClient() {
        return httpClientFactory.getClient();
    }

    /**
     * Sets the HttpClientFactory and creates one DefaultHttpClient and cashes it.
     *
     * @param httpClientFactory New HttpClients will be created using the this Factory.
     */
    @Override
    public void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public void setHttpResponseProcessor(HttpResponseProcessor httpResponseProcessor) {
        this.httpResponseProcessor = httpResponseProcessor;
    }

    @Override
    public void setHostUri(String hostUri) throws URISyntaxException {
        this.hostUri = new URI(hostUri);
    }

    public HttpResponseProcessor getHttpResponseProcessor() {
        return httpResponseProcessor;
    }

    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    public String getHostUri() {
        return hostUri.toString();
    }

    public void setNumberOfClients(int numberOfClients) {
        this.numberOfClients = numberOfClients;
    }

    class RoundRobinLogic {

        private final List<HttpClient> httpClients;
        private final AtomicLong currentCounter = new AtomicLong(0);

        RoundRobinLogic(List<HttpClient> httpClients) {
            this.httpClients = httpClients;
        }

        HttpClient nextClient() {
            // I know this is not 100% thread safe, but this want be an issue because its just used to share the load and not for anything else
            currentCounter.compareAndSet(Integer.MAX_VALUE, 0);
            return httpClients.get((int) currentCounter.getAndIncrement() % httpClients.size());
        }
    }
}
