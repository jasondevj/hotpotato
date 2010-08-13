hotpotato
=========

**hotpotato** or **hptt** (from the common misspelling of http) is (supposed to be) a Java high-performance and throughput-oriented HTTP client library.

It is aimed mostly at heavily concurrent server-side usage.

Project page: [http://hotpotato.factor45.org](http://hotpotato.factor45.org)

Dependencies
------------

* JDK 1.6
* [Netty 3.2.1 Final](http://jboss.org/netty/downloads.html)

License
-------

**hotpotato** is licensed under the [Apache License version 2.0](http://www.apache.org/licenses/LICENSE-2.0) as published by the Apache Software Foundation.

Quick & Dirty examples
----------------------

### Synchronous mode

This example contains all the steps to execute a request, from creation to cleanup.
This is the synchronous mode, which means that the calling thread will block until the request completes.

    // Create & initialise the client
    HttpClient client = new DefaultHttpClient();
    client.init();

    // Setup the request
    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_0,
                                                 HttpMethod.GET, "/");

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

### Asynchronous mode

In asynchronous mode, an event listener is attached to the object returned by the http client when a request execution is submitted. Attaching this listener allows the programmer to define some computation to occur when the request finishes.

Only the relevant parts are shown here.

    // Execute the request
    HttpRequestFuture<String> future = client.execute("hotpotato.factor45.org", 80, request,
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

Note that you should **never** perform non-CPU bound operations in the listeners.

### Integration with IoC containers

**hotpotato** was developed to be fully IoC compliant.

Here's a simple example of how to configure a client in [Spring](http://www.springsource.org/):

    <bean id="httpClient" class="org.factor45.hotpotato.client.DefaultHttpClient"
          init-method="init" destroy-method="terminate">
      <property ... />
    </bean>

Or using a client factory, in case you want multiple clients:

    <bean id="httpClientFactory" class="org.factor45.hotpotato.client.factory.DefaultHttpClientFactory">
      <property ... />
    </bean>

HttpClientFactories will configure each client produced exactly how they were configured - they have the same options as (or more than) the HttpClients they generate.
Instead of having some sort of Configuration object, you configure the factory and then call getClient() in it to obtain a pre-configured client.

You can also create a client for a component, based on a predefined factory:

    <bean id="someBean" class="org.factor45.SomeComponent">
      <property name="httpClient">
        <bean factory-bean="httpClientFactory" factory-method="getClient" />
      </property>
      <property ... />
    </bean>

Note that you can accomplish the same effect as the factory example by simply using an abstract definition of a HttpClient bean and then using Spring inheritance.
