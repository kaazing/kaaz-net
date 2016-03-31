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
package org.kaazing.netx.http.internal.auth;

import org.kaazing.netx.http.auth.ChallengeRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RealmUtils {

    private RealmUtils() {
        // prevent object creation
    }

    private static final String REALM_REGEX = "(.*)\\s?(?i:realm=)(\"(.*)\")(.*)";
    private static final Pattern REALM_PATTERN = Pattern.compile(REALM_REGEX);

    /**
     * A realm parameter is a valid authentication parameter for all authentication schemes
     * according to <a href="http://tools.ietf.org/html/rfc2617#section-2.1">RFC 2617 Section 2.1"</a>.
     *
     * {@code}
     *     The realm directive (case-insensitive) is required for all
     *     authentication schemes that issue a challenge. The realm value
     *     (case-sensitive), in combination with the canonical root URL (the
     *     absoluteURI for the server whose abs_path is empty) of the server
     *     being accessed, defines the protection space.
     * {@code}
     *
     * @param challengeRequest the challenge request to extract a realm from
     *
     * @return the unquoted realm parameter value if present, or {@code null} if no such parameter exists.
     */
    public static String getRealm(ChallengeRequest challengeRequest) {
        String authenticationParameters = challengeRequest.getAuthenticationParameters();
        if (authenticationParameters == null) {
            return null;
        }
        Matcher m = REALM_PATTERN.matcher(authenticationParameters);
        if (m.matches() && m.groupCount() >= 3) {
            return m.group(3);
        }
        return null;
    }
}
