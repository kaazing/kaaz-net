/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.netx.http.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.kaazing.netx.http.HttpRedirectPolicy.ALWAYS;
import static org.kaazing.netx.http.HttpRedirectPolicy.DOMAIN;
import static org.kaazing.netx.http.HttpRedirectPolicy.NEVER;
import static org.kaazing.netx.http.HttpRedirectPolicy.ORIGIN;
import static org.kaazing.netx.http.internal.HttpRedirectPolicyUtils.shouldFollowRedirect;

import java.net.URL;

import org.junit.Test;

public class HttpRedirectPolicyUtilsTest {

    @Test
    public void testAlways() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("https://example.net:9090/different/path");
        assertTrue(shouldFollowRedirect(ALWAYS, currentURL, redirectURL));
    }

    @Test
    public void testNever() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://example.com:8080/different/path");
        assertFalse(shouldFollowRedirect(NEVER, currentURL, redirectURL));
    }

    @Test
    public void testSameOrigin() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://example.com:8080/path?query");
        assertTrue(shouldFollowRedirect(ORIGIN, currentURL, redirectURL));
    }

    @Test
    public void testSameOriginWithDifferentPorts() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://example.com:8081/path");
        assertFalse(shouldFollowRedirect(ORIGIN, currentURL, redirectURL));
    }

    @Test
    public void testSameOriginWithDifferentHost() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://example.net:8080/different/path");
        assertFalse(shouldFollowRedirect(ORIGIN, currentURL, redirectURL));
    }

    @Test
    public void testSameDomain() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://example.com:8081/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSameDomainWithSameHostnameAndPort() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("https://example.com:8080/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSameDomainWithDifferentHosts() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://example.net:8080/path");
        assertFalse(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testPeerDomain() throws Exception {
        URL currentURL = new URL("http://east.example.com:8080/path");
        URL redirectURL = new URL("https://west.example.com:9090/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testPeerDomainSameHostnameAndPort() throws Exception {
        URL currentURL = new URL("http://east.example.com:8080/path");
        URL redirectURL = new URL("http://east.example.com:8080/different/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testPeerDomainSameHostname() throws Exception {
        URL currentURL = new URL("http://east.example.com:8080/path");
        URL redirectURL = new URL("https://west.example.com:9090/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testPeerDomainNegative() throws Exception {
        URL currentURL = new URL("http://south.east.example.com:8080/path");
        URL redirectURL = new URL("https://north.west.example.com:9090/path");
        assertFalse(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSubDomain() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("https://west.example.com:9090/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSubDomainSameHostnameAndPort() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("http://east.example.com:8080/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSubDomainSameHostname() throws Exception {
        URL currentURL = new URL("http://east.example.com:8080/path");
        URL redirectURL = new URL("https://east.example.com:9090/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSubSubDomain() throws Exception {
        URL currentURL = new URL("http://example.com:8080/path");
        URL redirectURL = new URL("https://north.west.example.com:9090/path");
        assertTrue(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }

    @Test
    public void testSubDomainNegative() throws Exception {
        URL currentURL = new URL("http://east.example.com:8080/path");
        URL redirectURL = new URL("https://north.west.example.com:9090/path");
        assertFalse(shouldFollowRedirect(DOMAIN, currentURL, redirectURL));
    }
}
