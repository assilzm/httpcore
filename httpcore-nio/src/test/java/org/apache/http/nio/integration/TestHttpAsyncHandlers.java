/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncExpectationVerifier;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import org.apache.http.nio.testserver.LoggingClientConnectionFactory;
import org.apache.http.nio.testserver.LoggingServerConnectionFactory;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * HttpCore NIO integration tests for async handlers.
 */
public class TestHttpAsyncHandlers extends HttpCoreNIOTestBase {

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        shutDownClient();
        shutDownServer();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpServerConnection> createServerConnectionFactory() throws Exception {
        return new LoggingServerConnectionFactory();
    }

    @Override
    protected NHttpConnectionFactory<DefaultNHttpClientConnection> createClientConnectionFactory() throws Exception {
        return new LoggingClientConnectionFactory();
    }

    private InetSocketAddress start(
            final HttpProcessor clientProtocolProcessor,
            final HttpProcessor serverProtocolProcessor,
            final HttpAsyncRequestHandlerMapper requestHandlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier) throws Exception {
        this.server.start(serverProtocolProcessor, requestHandlerResolver, expectationVerifier);
        this.client.start(clientProtocolProcessor);

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());
        return (InetSocketAddress) endpoint.getAddress();
    }

    private InetSocketAddress start(
            final HttpAsyncRequestHandlerMapper requestHandlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier) throws Exception {
        return start(null, null, requestHandlerResolver, expectationVerifier);
    }

    private static String createRequestUri(final String pattern, final int count) {
        return pattern + "x" + count;
    }

    private static String createExpectedString(final String pattern, final int count) {
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        return buffer.toString();
    }

    @Test
    public void testHttpGets() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpHeads() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("HEAD", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHttpPostsWithContentLength() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsChunked() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            entity.setChunked(true);
            request.setEntity(entity);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsHTTP10() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count), HttpVersion.HTTP_1_0);
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsNoEntity() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            request.setEntity(null);
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        final HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        final InetSocketAddress address = start(clientHttpProc, null, registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        final Future<HttpResponse> future = this.client.execute(target, request);

        final HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));

        final HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new HttpRequestInterceptor() {

                    public void process(
                            final HttpRequest request,
                            final HttpContext context) throws HttpException, IOException {
                        request.addHeader(HTTP.TRANSFER_ENCODING, "identity");
                    }

                },
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        final InetSocketAddress address = start(clientHttpProc, null, registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri(pattern, count));
        request.setEntity(null);

        final Future<HttpResponse> future = this.client.execute(target, request);

        final HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());
        final String expectedPattern = createExpectedString(pattern, count);

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", createRequestUri(pattern, count));
            final NStringEntity entity = new NStringEntity(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);

            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                final HttpRequest request = httpexchange.getRequest();
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                final String s = request.getRequestLine().getUri();
                if (!s.equals("AAAAAx10")) {
                    if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                        ver = HttpVersion.HTTP_1_1;
                    }
                    final BasicHttpResponse response = new BasicHttpResponse(ver,
                            HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                    response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                    httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                } else {
                    httpexchange.submitResponse();
                }
            }

        };

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, expectationVerifier);

        final BasicHttpEntityEnclosingRequest request1 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpEntityEnclosingRequest request2 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpEntityEnclosingRequest request3 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        final HttpRequest[] requests = new HttpRequest[] { request1, request2, request3 };

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (final HttpRequest request : requests) {
            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        final Future<HttpResponse> future1 = queue.remove();
        final HttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());

        final Future<HttpResponse> future2 = queue.remove();
        final HttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());

        final Future<HttpResponse> future3 = queue.remove();
        final HttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpHeadsDelayedResponse() throws Exception {

        class DelayedRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

            private final SimpleRequestHandler requestHandler;

            public DelayedRequestHandler() {
                super();
                this.requestHandler = new SimpleRequestHandler();
            }

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                    ver = HttpVersion.HTTP_1_1;
                }
                final BasicHttpResponse response = new BasicHttpResponse(ver, HttpStatus.SC_OK, "OK");
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(100); } catch(final InterruptedException ie) {}
                        // Set the entity after delaying...
                        try {
                            requestHandler.handle(request, response, context);
                        } catch (final Exception ex) {
                            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                        }
                        httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                    }
                }.start();
            }

        }

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new DelayedRequestHandler());
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("HEAD", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testHttpPostsWithExpectationVerificationDelayedResponse() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = new HttpAsyncExpectationVerifier() {

            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                new Thread() {
                    @Override
                    public void run() {
                        // Wait a bit, to make sure this is delayed.
                        try { Thread.sleep(100); } catch(final InterruptedException ie) {}
                        // Set the entity after delaying...
                        final HttpRequest request = httpexchange.getRequest();
                        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                        final String s = request.getRequestLine().getUri();
                        if (!s.equals("AAAAAx10")) {
                            if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                                ver = HttpVersion.HTTP_1_1;
                            }
                            final BasicHttpResponse response = new BasicHttpResponse(ver,
                                    HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");
                            response.setEntity(new NStringEntity("Expectation failed", ContentType.TEXT_PLAIN));
                            httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
                        } else {
                            httpexchange.submitResponse();
                        }
                    }
                }.start();
            }

        };

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        final InetSocketAddress address = start(registry, expectationVerifier);

        final BasicHttpEntityEnclosingRequest request1 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request1.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpEntityEnclosingRequest request2 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("AAAAA", 10));
        request2.setEntity(new NStringEntity(createExpectedString("AAAAA", 10)));
        final BasicHttpEntityEnclosingRequest request3 = new BasicHttpEntityEnclosingRequest(
                "POST", createRequestUri("BBBBB", 10));
        request3.setEntity(new NStringEntity(createExpectedString("BBBBB", 10)));

        final HttpRequest[] requests = new HttpRequest[] { request1, request2, request3 };

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (final HttpRequest request : requests) {
            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future = this.client.execute(target, request, context);
            queue.add(future);
        }

        final Future<HttpResponse> future1 = queue.remove();
        final HttpResponse response1 = future1.get();
        Assert.assertEquals(HttpStatus.SC_OK, response1.getStatusLine().getStatusCode());

        final Future<HttpResponse> future2 = queue.remove();
        final HttpResponse response2 = future2.get();
        Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());

        final Future<HttpResponse> future3 = queue.remove();
        final HttpResponse response3 = future3.get();
        Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response3.getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpExceptionInHandler() throws Exception {

        class FailingRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

            public FailingRequestHandler() {
                super();
            }

            public HttpAsyncRequestConsumer<HttpRequest> processRequest(
                    final HttpRequest request,
                    final HttpContext context) {
                return new BasicAsyncRequestConsumer();
            }

            public void handle(
                    final HttpRequest request,
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException, IOException {
                throw new HttpException("Boom");
            }

        }

        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new FailingRequestHandler());
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 1; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testNoServiceHandler() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", createRequestUri(pattern, count));
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testResponseNoContent() throws Exception {
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        registry.register("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }

        }));
        final InetSocketAddress address = start(registry, null);

        this.client.setMaxPerRoute(3);
        this.client.setMaxTotal(3);

        final HttpHost target = new HttpHost("localhost", address.getPort());

        final Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
            final Future<HttpResponse> future = this.client.execute(target, request);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<HttpResponse> future = queue.remove();
            final HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertNull(response.getEntity());
        }
    }

}
