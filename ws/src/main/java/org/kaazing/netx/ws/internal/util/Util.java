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
package org.kaazing.netx.ws.internal.util;

import java.net.URI;
import java.net.URISyntaxException;

public final class Util {

    private Util() {
        // utility
    }

    public static URI changeScheme(URI location, String newScheme) {
        String userInfo = location.getUserInfo();
        String host = location.getHost();
        int port = location.getPort();
        String path = location.getPath();
        String query = location.getQuery();
        String fragment = location.getFragment();

        try {
            return new URI(newScheme, userInfo, host, port, path, query, fragment);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
