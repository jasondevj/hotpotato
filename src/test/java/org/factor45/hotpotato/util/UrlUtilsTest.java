/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.factor45.hotpotato.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class UrlUtilsTest {

    @Test
    public void testSplitUrl() throws Exception {
        HostPortAndUri hostPortAndUri = HostPortAndUri.splitUrl("http://hotpotato.factor45.org/doc");
        assertNotNull(hostPortAndUri);
        // Not exactly the same, as port 80 is introduced...
        assertEquals("http://hotpotato.factor45.org:80/doc", hostPortAndUri.asUrl());
        assertEquals("http", hostPortAndUri.getScheme());
        assertEquals("hotpotato.factor45.org", hostPortAndUri.getHost());
        assertEquals(80, hostPortAndUri.getPort());
        assertEquals("/doc", hostPortAndUri.getUri());
        assertFalse(hostPortAndUri.isHttps());
        System.err.println(hostPortAndUri + " = " + hostPortAndUri.asUrl());
    }

    @Test
    public void testSplitUrlNoUri() throws Exception {
        HostPortAndUri hostPortAndUri = HostPortAndUri.splitUrl("https://hotpotato.factor45.org");
        assertNotNull(hostPortAndUri);
        // Not exactly the same, as port 80 is introduced...
        assertEquals("https://hotpotato.factor45.org:80/", hostPortAndUri.asUrl());
        assertEquals("https", hostPortAndUri.getScheme());
        assertEquals("hotpotato.factor45.org", hostPortAndUri.getHost());
        assertEquals(80, hostPortAndUri.getPort());
        assertEquals("/", hostPortAndUri.getUri());
        assertTrue(hostPortAndUri.isHttps());
        System.err.println(hostPortAndUri + " = " + hostPortAndUri.asUrl());
    }

    @Test
    public void testSplitUrlDifferentPort() throws Exception {
        HostPortAndUri hostPortAndUri = HostPortAndUri
                .splitUrl("https://hotpotato.factor45.org:8085/this/is/a/uri?a=1&b=2");
        assertNotNull(hostPortAndUri);
        // Not exactly the same, as port 80 is introduced...
        assertEquals("https://hotpotato.factor45.org:8085/this/is/a/uri?a=1&b=2", hostPortAndUri.asUrl());
        assertEquals("https", hostPortAndUri.getScheme());
        assertEquals("hotpotato.factor45.org", hostPortAndUri.getHost());
        assertEquals(8085, hostPortAndUri.getPort());
        assertEquals("/this/is/a/uri?a=1&b=2", hostPortAndUri.getUri());
        assertTrue(hostPortAndUri.isHttps());
        System.err.println(hostPortAndUri + " = " + hostPortAndUri.asUrl());
    }
}
