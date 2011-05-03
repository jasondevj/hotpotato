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
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * $LastChangedDate$
 * $LastChangedBy$
 * $LastChangedRevision$
 */
public class ClientManagerExample {

    private static final String URL = "https://127.0.0.1:8443/";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static HttpClientManager clientManager;


    public static void main(String[] args) throws URISyntaxException, InterruptedException {
        clientManager = new DefaultHttpClientManager();
        final DefaultHttpClientFactory defaultHttpClientFactory = new DefaultHttpClientFactory();
        defaultHttpClientFactory.setMaxConnectionsPerHost(20);
        defaultHttpClientFactory.setMaxIoWorkerThreads(20);
        defaultHttpClientFactory.setMaxQueuedRequests(200);
        defaultHttpClientFactory.setUseSsl(true);
        defaultHttpClientFactory.setUseNio(true);
        defaultHttpClientFactory.setAggregateChunks(true);
//        defaultHttpClientFactory.setConnectionTimeoutInMillis(20000);
        defaultHttpClientFactory.setRequestTimeoutInMillis(20000);
//        defaultHttpClientFactory.setDebug(true);
        clientManager.setHostUri(URL);
        clientManager.setHttpClientFactory(defaultHttpClientFactory);
        clientManager.setNumberOfClients(3);
        clientManager.init();

        final AtomicLong exceptionCounter = new AtomicLong(0);
        final AtomicLong nullPointerExceptionCounter = new AtomicLong(0);

        for (int y = 0; y < 100; y++) {

            System.out.println("Sending new set of request...");
            for (int i = 0; i < 250; i++) {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sendPostRequest(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, URL));
                            sendPostRequest(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, URL));
                        } catch (NullPointerException e) {
                            System.out.println("nullPointerExceptionCounter.getAndIncrement() = " + nullPointerExceptionCounter.getAndIncrement());
                            e.printStackTrace();
                        } catch (Throwable e) {
                            System.out.println("exceptionCounter.getAndIncrement() = " + exceptionCounter.getAndIncrement());
                            System.err.println(e);
                        }
                    }
                });
            }
            System.out.println("Request sent..");
            Thread.sleep(180000);
        }
//        clientManager.terminate();
    }

    private static void sendPostRequest(final DefaultHttpRequest request) {

        final HttpRequestFuture requestFuture = clientManager.execute(request, new ResponseProcessor());
        requestFuture.awaitUninterruptibly(10000);
        final HttpResponse response = requestFuture.getResponse();


        if (response.getStatus().getCode() != 200) {
            System.out.println("Error : " + response.getStatus());
            System.out.println("Error Response : [" + requestFuture.getProcessedResult() + "]");
        }
    }

    private static class ResponseProcessor implements HttpResponseProcessor {

        private String message;

        @Override
        public boolean willProcessResponse(HttpResponse response) throws Exception {
            return true;
        }

        @Override
        public void addData(ChannelBuffer content) throws Exception {

        }

        @Override
        public void addLastData(ChannelBuffer content) throws Exception {
            message = content.toString("UTF-8");
        }

        @Override
        public Object getProcessedResponse() {
            return message;
        }
    }
}
