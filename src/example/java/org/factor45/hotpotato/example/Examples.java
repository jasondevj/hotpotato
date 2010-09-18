package org.factor45.hotpotato.example;

import org.factor45.hotpotato.client.DefaultHttpClient;
import org.factor45.hotpotato.client.HttpClient;
import org.factor45.hotpotato.client.connection.factory.PipeliningHttpConnectionFactory;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.request.HttpRequestFutureListener;
import org.factor45.hotpotato.response.BodyAsStringProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.concurrent.CountDownLatch;

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

    public static void example4() {
        final DefaultHttpClient client = new DefaultHttpClient();
        client.setRequestTimeoutInMillis(5000);
        client.init();

        final CountDownLatch latch = new CountDownLatch(3);

        HttpRequest request;
        HttpRequestFuture<String> future;

        request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        request.addHeader(HttpHeaders.Names.HOST, "hotpotato.factor45.org");
        future = client.execute("hotpotato.factor45.org", 80, request, new BodyAsStringProcessor());
        future.addListener(new HttpRequestFutureListener<String>() {
            @Override
            public void operationComplete(HttpRequestFuture<String> future) throws Exception {
                System.out.println("\nHotpotato request: " + future);
                if (future.isSuccess()) {
                    System.out.println(future.getResponse());
                } else {
                    System.out.println(future.getResponse());
                    future.getCause().printStackTrace();
                }
                if (future.isSuccessfulResponse()) {
                    System.out.println(future.getProcessedResult());
                }
                latch.countDown();
            }
        });

        request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                                         "http://www.google.pt/webhp?hl=pt-PT&tab=iw");
        request.addHeader(HttpHeaders.Names.HOST, "www.google.pt");
        future = client.execute("www.google.pt", 80, request, new BodyAsStringProcessor());
        future.addListener(new HttpRequestFutureListener<String>() {
            @Override
            public void operationComplete(HttpRequestFuture<String> future) throws Exception {
                System.out.println("\nGoogle request: " + future);
                if (future.isSuccess()) {
                    System.out.println(future.getResponse());
                } else {
                    System.out.println(future.getResponse());
                    future.getCause().printStackTrace();
                }
                if (future.isSuccessfulResponse()) {
                    System.out.println(future.getProcessedResult());
                }
                latch.countDown();
            }
        });

        request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                                         "http://twitter.com/");
        future = client.execute("twitter.com", 80, request, new BodyAsStringProcessor());
        request.addHeader(HttpHeaders.Names.HOST, "twitter.com");
        future.addListener(new HttpRequestFutureListener<String>() {
            @Override
            public void operationComplete(HttpRequestFuture<String> future) throws Exception {
                System.out.println("\nTwitter request: " + future);
                if (future.isSuccess()) {
                    System.out.println(future.getResponse());
                } else {
                    System.out.println(future.getResponse());
                    future.getCause().printStackTrace();
                }
                if (future.isSuccessfulResponse()) {
                    System.out.println(future.getProcessedResult());
                }
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client.terminate();
    }

    public static void example5() {
        // Pipelining
        DefaultHttpClient client = new DefaultHttpClient();
        PipeliningHttpConnectionFactory connectionFactory = new PipeliningHttpConnectionFactory();
        client.setConnectionFactory(connectionFactory);

        client.setRequestTimeoutInMillis(5000);
        client.init();

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        request.addHeader(HttpHeaders.Names.HOST, "hotpotato.factor45.org");
        HttpRequestFuture<String> future = client
                .execute("hotpotato.factor45.org", 80, request, new BodyAsStringProcessor());

        future.awaitUninterruptibly();
        if (future.isSuccess()) {
            System.out.println(future.getResponse());
        } else {
            if (future.getResponse() != null) {
                System.out.println(future.getResponse());
            }
            future.getCause().printStackTrace();
        }

        client.terminate();
    }

    public static void example6() {
        // HttpSession API
    }

    public static void main(String[] args) {
        example5();
    }
}
