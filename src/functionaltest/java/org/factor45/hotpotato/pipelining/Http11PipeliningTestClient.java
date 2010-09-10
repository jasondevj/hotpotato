package org.factor45.hotpotato.pipelining;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class Http11PipeliningTestClient {

    // configuration ---------------------------------------------------------

    private String host;
    private int port;

    // internal vars ---------------------------------------------------------

    private ChannelFactory factory;
    private ClientHandler handler;

    // constructors ----------------------------------------------------------

    public Http11PipeliningTestClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // public methods --------------------------------------------------------

    public boolean init() {
        this.factory = new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());
        this.handler = new ClientHandler();
        ClientBootstrap bootstrap = new ClientBootstrap(this.factory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("codec", new HttpClientCodec());
                pipeline.addLast("handler", handler);
                return pipeline;
            }
        });

        ChannelFuture future = bootstrap
                .connect(new InetSocketAddress(this.host, this.port));
        return future.awaitUninterruptibly().isSuccess();
    }

    public void terminate() {
        if (this.handler != null) {
            this.handler.terminate();
        }

        if (this.factory != null) {
            this.factory.releaseExternalResources();
        }
    }

    public void sendRequests(int requests, long interval) {
        for (int i = 0; i < requests; i++) {
            if (interval > 0) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    return;
                }
            }
            this.handler.sendRequest();
        }
    }

    // private classes -------------------------------------------------------

    private final class ClientHandler extends SimpleChannelUpstreamHandler {

        // internal vars -----------------------------------------------------

        private Channel channel;
        private final AtomicInteger uriGenerator;
        private AtomicInteger lastReceived;

        // constructors ------------------------------------------------------

        private ClientHandler() {
            this.uriGenerator = new AtomicInteger();
        }

        // SimpleChannelUpstreamHandler --------------------------------------

        @Override
        public void channelConnected(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
            System.out.println("Channel to server open: " + e.getChannel());
            this.channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            HttpResponse response = (HttpResponse) e.getMessage();
            String body = response.getContent().toString(CharsetUtil.UTF_8);
            int number = Integer.parseInt(body.substring(1));

            if (this.lastReceived != null) {
                if (number <= this.lastReceived.getAndIncrement()) {
                    System.err.println(">> OUT OF ORDER! Expecting " +
                                       (this.lastReceived.get() - 1) +
                                       " but got " + number);
                    this.lastReceived.set(number);
                } else {
                    System.out.println(">> " + number + " (IN ORDER)");
                }
            } else {
                this.lastReceived = new AtomicInteger(number);
                System.out.println(">> " +  number + " (FIRST, IN ORDER)");
            }
        }

        // public methods ----------------------------------------------------

        public void sendRequest() {
            if ((this.channel == null) || !this.channel.isConnected()) {
                System.err.println("sendRequest() not yet connected!");
                return;
            }

            String uri = "/" + this.uriGenerator.incrementAndGet();
            final HttpRequest request =
                    new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                                           HttpMethod.GET, uri);
            ChannelFuture future = this.channel.write(request);
            future.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future)
                        throws Exception {
                    if (future.isSuccess()) {
                        System.out.println("<< " + request.getMethod() +
                                           " " + request.getUri());
                    }
                }
            });
        }

        public void terminate() {
            if ((this.channel != null) && this.channel.isConnected()) {
                this.channel.close();
            }
        }
    }

    // main ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        final Http11PipeliningTestClient client =
                new Http11PipeliningTestClient("localhost", 8080);
        if (!client.init()) {
            System.err.println("Failed to initialise client.");
            return;
        }

        // sleep a bit before starting
        Thread.sleep(500L);

        // send 1000 requests, with intervals of 1ms
        client.sendRequests(1000, 1);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                client.terminate();
            }
        });
    }
}
