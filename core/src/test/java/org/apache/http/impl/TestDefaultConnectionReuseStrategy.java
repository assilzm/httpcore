/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestDefaultConnectionReuseStrategy extends TestCase {

    /** A mock connection that is open and not stale. */
    private HttpConnection mockConnection;

    /** The reuse strategy to be tested. */
    private ConnectionReuseStrategy reuseStrategy;



    public TestDefaultConnectionReuseStrategy(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDefaultConnectionReuseStrategy.class);
    }

    public void setUp() {
        // open and not stale is required for most of the tests here
        mockConnection = new MockConnection(true, false);
        reuseStrategy = new DefaultConnectionReuseStrategy();
    }

    public void tearDown() {
        mockConnection = null;
    }


    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultConnectionReuseStrategy.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testIllegalConnectionArg() throws Exception {

        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", false, -1);

        try {
            reuseStrategy.keepAlive(null, response);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testIllegalResponseArg() throws Exception {
        try {
            reuseStrategy.keepAlive(mockConnection, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testNoContentLengthResponseHttp1_0() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK", false, -1);

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }

    public void testNoContentLengthResponseHttp1_1() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", false, -1);

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }

    public void testChunkedContent() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);

        assertTrue(reuseStrategy.keepAlive(mockConnection, response));
    }

    public void testClosedConnection() throws Exception {

        // based on testChunkedContent which is known to return true
        // the difference is in the mock connection passed here
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);

        HttpConnection mockonn = new MockConnection(false, false);
        assertFalse("closed connection should not be kept alive",
                    reuseStrategy.keepAlive(mockonn, response));
    }

    public void testStaleConnection() throws Exception {

        // based on testChunkedContent which is known to return true
        // the difference is in the mock connection passed here
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);

        HttpConnection mockonn = new MockConnection(true, true);
        assertTrue("stale connection should not be detected",
                    reuseStrategy.keepAlive(mockonn, response));
    }

    public void testIgnoreInvalidKeepAlive() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK", false, -1);
        response.addHeader("Connection", "keep-alive");

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }
    
    public void testExplicitClose() throws Exception {
        // Use HTTP 1.1
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK", true, -1);
        response.addHeader("Connection", "close");

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }
    
    public void testExplicitKeepAlive() throws Exception {
        // Use HTTP 1.0
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK", false, 10);
        response.addHeader("Connection", "keep-alive");

        assertTrue(reuseStrategy.keepAlive(mockConnection, response));
    }

    public void testHTTP10Default() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK");

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }
    
    public void testHTTP11Default() throws Exception {
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_1, 200, "OK");
        assertTrue(reuseStrategy.keepAlive(mockConnection, response));
    }

    public void testFutureHTTP() throws Exception {
        HttpResponse response =
            createResponse(new HttpVersion(3, 45), 200, "OK");

        assertTrue(reuseStrategy.keepAlive(mockConnection, response));
    }
    
    public void testBrokenConnectionDirective1() throws Exception {
        // Use HTTP 1.0
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Connection", "keep--alive");

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }

    public void testBrokenConnectionDirective2() throws Exception {
        // Use HTTP 1.0
        HttpResponse response =
            createResponse(HttpVersion.HTTP_1_0, 200, "OK");
        response.addHeader("Connection", null);

        assertFalse(reuseStrategy.keepAlive(mockConnection, response));
    }


    /**
     * Creates a response without an entity.
     *
     * @param version   the HTTP version
     * @param status    the status code
     * @param message   the status message
     *
     * @return  a response with the argument attributes, but no headers
     */
    private final static HttpResponse createResponse(HttpVersion version,
                                                     int status,
                                                     String message) {

        StatusLine statusline = new BasicStatusLine(version, status, message);
        HttpResponse response = new BasicHttpResponse(statusline);

        return response;

    } // createResponse/empty


    /**
     * Creates a response with an entity.
     *
     * @param version   the HTTP version
     * @param status    the status code
     * @param message   the status message
     * @param chunked   whether the entity should indicate chunked encoding
     * @param length    the content length to be indicated by the entity
     *
     * @return  a response with the argument attributes, but no headers
     */
    private final static HttpResponse createResponse(HttpVersion version,
                                                     int status,
                                                     String message,
                                                     boolean chunked,
                                                     int length) {

        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(chunked);
        entity.setContentLength(length);
        HttpResponse response = createResponse(version, status, message);
        response.setEntity(entity);

        return response;

    } // createResponse/entity


    /**
     * A mock connection.
     * This is neither client nor server connection, since the default
     * strategy is agnostic. It does not allow modification of it's state,
     * since the strategy is supposed to decide about keep-alive, but not
     * to modify the connection's state.
     */
    private final static class MockConnection implements HttpConnection {

        private boolean iAmOpen;
        private boolean iAmStale;

        public MockConnection(boolean open, boolean stale) {
            iAmOpen = open;
            iAmStale = stale;
        }

        public final boolean isOpen() {
            return iAmOpen;
        }

        public final boolean isStale() {
            return iAmStale;
        }

        public final void close() {
            throw new UnsupportedOperationException
                ("connection state must not be modified");
        }

        public final void shutdown() {
            throw new UnsupportedOperationException
                ("connection state must not be modified");
        }
    } // class MockConnection

} // class TestDefaultConnectionReuseStrategy
