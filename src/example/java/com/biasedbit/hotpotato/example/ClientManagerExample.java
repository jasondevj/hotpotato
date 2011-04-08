/*
 *   (C) Copyright 2010-2011 hSenid Software International (Pvt) Limited.
 *   All Rights Reserved.
 *
 *   These materials are unpublished, proprietary, confidential source code of
 *   hSenid Software International (Pvt) Limited and constitute a TRADE SECRET
 *   of hSenid Software International (Pvt) Limited.
 *
 *   hSenid Software International (Pvt) Limited retains all title to and intellectual
 *   property rights in these materials.
 *
 */
package com.biasedbit.hotpotato.example;

import com.biasedbit.hotpotato.client.factory.DefaultHttpClientFactory;
import com.biasedbit.hotpotato.client.manager.DefaultHttpClientManager;
import com.biasedbit.hotpotato.client.manager.HttpClientManager;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.BodyAsStringProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.net.URISyntaxException;

/**
 * $LastChangedDate$
 * $LastChangedBy$
 * $LastChangedRevision$
 */
public class ClientManagerExample {

    private static final String URL = "http://127.0.0.1:8080/test/";


    public static void main(String[] args) throws URISyntaxException {
        final HttpClientManager clientManager  = new DefaultHttpClientManager();
        final DefaultHttpClientFactory defaultHttpClientFactory = new DefaultHttpClientFactory();
        defaultHttpClientFactory.setMaxConnectionsPerHost(20);
        defaultHttpClientFactory.setMaxIoWorkerThreads(20);
        defaultHttpClientFactory.setMaxQueuedRequests(20);
        defaultHttpClientFactory.setAggregateChunks(true);
        clientManager.setHostUri(URL);
        clientManager.setHttpClientFactory(defaultHttpClientFactory);
        clientManager.setNumberOfClients(3);
        sendRequest(clientManager);
    }

    private static void sendRequest(HttpClientManager clientManager) {
        final BodyAsStringProcessor httpResponseProcessor = new BodyAsStringProcessor();
        final HttpRequestFuture requestFuture = clientManager.execute(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, URL), httpResponseProcessor);
        requestFuture.awaitUninterruptibly(3000);
        final BodyAsStringProcessor response = (BodyAsStringProcessor) requestFuture.getResponse();

        System.out.println(response.getProcessedResponse());
    }
}
