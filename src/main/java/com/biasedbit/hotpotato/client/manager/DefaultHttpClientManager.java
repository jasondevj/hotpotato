package com.biasedbit.hotpotato.client.manager;

import com.biasedbit.hotpotato.client.HttpClient;
import com.biasedbit.hotpotato.client.factory.HttpClientFactory;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.DiscardProcessor;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * DefaultHttpClientManager works as a wrapper for HttpClient so that when using the client only the request need to
 * be set and other parameters will be set throe dependency injection. This Manager also can be used to do the
 * load balancing between multiple clients and fail-over
 */
public class DefaultHttpClientManager implements HttpClientManager {

    private HttpClientFactory httpClientFactory;
    private HttpResponseProcessor httpResponseProcessor;
    private URI hostUri;
    private HttpClient defaultHttpClient;

    /**
     * Execute the request, before executing the Host URI must be set and if a HttpResponseProcessor is not set
     * the Responce will be discarded.
     * @param request request which was is suppose to be sent.
     * @return
     */
    @Override
    public HttpRequestFuture execute(HttpRequest request) {
        if (httpResponseProcessor == null) {
            validateHostUri();
            return defaultHttpClient.execute(hostUri.getHost(), hostUri.getPort(), request,
                    DiscardProcessor.getInstance());
        } else {
            return defaultHttpClient.execute(hostUri.getHost(), hostUri.getPort(), request, httpResponseProcessor);
        }
    }

    private void validateHostUri() {
        if (hostUri == null) {
            throw new RuntimeException("Default URI must be given to use this method or URI must be passed as a parameter");
        }
    }

    @Override
    public HttpRequestFuture execute(HttpRequest request, HttpResponseProcessor httpResponseProcessor) {
        return execute(hostUri.getHost(), hostUri.getPort(), request, httpResponseProcessor);
    }

    @Override
    public HttpRequestFuture execute(String host, int port, HttpRequest request, HttpResponseProcessor httpResponseProcessor) {
        return defaultHttpClient.execute(host, port, request, httpResponseProcessor);
    }

    /**
     * Return the HttpClient which was created initially.
     * @return Client Which was created initially.
     */
    @Override
    public HttpClient getDefaultHttpClient() {
        return defaultHttpClient;
    }

    /**
     * Creates a new Client from the factory and returns it.
     * @return New Client.
     */
    @Override
    public HttpClient createNewClient() {
        return httpClientFactory.getClient();
    }

    /**
     * Sets the HttpClientFactory and creates one DefaultHttpClient and cashes it.
     * @param httpClientFactory New HttpClients will be created using the this Factory.
     */
    @Override
    public void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        this.defaultHttpClient = httpClientFactory.getClient();
        defaultHttpClient.init();
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
}
