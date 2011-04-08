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
package com.biasedbit.hotpotato.client.manager;

import com.biasedbit.hotpotato.client.HttpClient;
import com.biasedbit.hotpotato.client.factory.HttpClientFactory;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;

import java.net.URISyntaxException;

/**
 * $LastChangedDate$
 * $LastChangedBy$
 * $LastChangedRevision$
 */
public interface HttpClientManager {

    HttpRequestFuture execute(HttpRequest request);

    void setHttpClientFactory(HttpClientFactory defaultHttpClientFactory);

    void setHttpResponseProcessor(HttpResponseProcessor httpResponseProcessor);

    void setHostUri(String hostUri) throws URISyntaxException;

    HttpRequestFuture execute(HttpRequest request, HttpResponseProcessor httpResponseProcessor);

    HttpClient createNewClient();

    HttpClient getDefaultHttpClient();

    HttpRequestFuture execute(String host, int port, HttpRequest request, HttpResponseProcessor httpResponseProcessor);

    void setNumberOfClients(int numberOfClients);
}
