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

import static org.junit.Assert.assertEquals;
import static org.kaazing.netx.http.internal.HttpOriginSecuritySpi.asOrigin;

import java.net.URL;

import org.junit.Test;

public class HttpOriginSecuritySpiTest {

    @Test
    public void shouldResolveJARBasedURL() throws Exception {
        URL url = new URL("jar:http://example.com:8080/path/to/jar!/path/to/class");
        assertEquals("http://example.com:8080", asOrigin(url));
    }

    @Test
    public void shouldResolveURL() throws Exception {
        URL url = new URL("http://example.com:8080/path/to/class");
        assertEquals("http://example.com:8080", asOrigin(url));
    }

    @Test
    public void shouldResolveURLMissingDefaultHttpPort() throws Exception {
        URL url = new URL("http://example.com/path/to/class");
        assertEquals("http://example.com", asOrigin(url));
    }

    @Test
    public void shouldResolveURLMissingDefaultHttpsPort() throws Exception {
        URL url = new URL("https://example.com/path/to/class");
        assertEquals("https://example.com", asOrigin(url));
    }

}
