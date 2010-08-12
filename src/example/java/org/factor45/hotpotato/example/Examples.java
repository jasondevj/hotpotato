package org.factor45.hotpotato.example;

import org.factor45.hotpotato.client.DefaultHttpClient;
import org.factor45.hotpotato.client.HttpClient;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.request.HttpRequestFutureListener;
import org.factor45.hotpotato.response.BodyAsStringProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class Examples {

    public static void example1() {
        // Create & initialise the client
        HttpClient client = new DefaultHttpClient();
        client.init();

        // Setup the request
        HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/");

        // Execute the request
        HttpRequestFuture future = client.execute("hotpotato.factor45.org", 80, request);
        future.awaitUninterruptibly();
        System.out.println(future);

        // Cleanup
        client.terminate();
    }

    public static void example2() {
        // Create & initialise the client
        HttpClient client = new DefaultHttpClient();
        client.init();

        // Setup the request
        HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/");

        // Execute the request, turning the result into a String
        HttpRequestFuture future = client.execute("hotpotato.factor45.org", 80, request,
                                                  new BodyAsStringProcessor());
        future.awaitUninterruptibly();
        // Print some details about the request
        System.out.println(future);

        // If response was >= 200 and <= 299, print the body
        if (future.isSuccessfulResponse()) {
            System.out.println(future.getProcessedResult());
        }

        // Cleanup
        client.terminate();
    }

    public static void example3() {
        // Create & initialise the client
        final HttpClient client = new DefaultHttpClient();
        client.init();

        // Setup the request
        HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/");

        // Execute the request
        HttpRequestFuture<String> future =
                client.execute("hotpotato.factor45.org", 80, request,
                               new BodyAsStringProcessor());
        future.addListener(new HttpRequestFutureListener<String>() {
            @Override
            public void operationComplete(HttpRequestFuture future) throws Exception {
                System.out.println(future);
                if (future.isSuccessfulResponse()) {
                    System.out.println(future.getProcessedResult());
                }
                client.terminate();
            }
        });
    }

    public static void main(String[] args) {
        example3();
    }
}
