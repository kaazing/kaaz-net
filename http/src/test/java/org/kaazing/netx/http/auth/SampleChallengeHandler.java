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
package org.kaazing.netx.http.auth;

import static java.lang.String.format;


public class SampleChallengeHandler extends ChallengeHandler implements LoginHandlerProvider {

    public static SampleChallengeHandler create() {
        return create(SampleChallengeHandler.class);
    }

    public static SampleChallengeHandler create(ClassLoader classLoader) {
        return create(SampleChallengeHandler.class, classLoader);
    }

    @Override
    public boolean canHandle(ChallengeRequest challengeRequest) {
        return challengeRequest != null && "Application test_challenge".equals(challengeRequest.getAuthenticationScheme());
    }

    @Override
    public ChallengeResponse handle(ChallengeRequest challengeRequest) {
        String authenticationScheme = challengeRequest.getAuthenticationScheme();
        String authenticationParameters = challengeRequest.getAuthenticationParameters();
        return new ChallengeResponse(format("%s %s", authenticationScheme, authenticationParameters).toCharArray(), this);
    }

    /**
     * If specified, this login handler is responsible for assisting in the
     * production of challenge responses.
     */
    private LoginHandler loginHandler;

    /**
     * Provide a login handler to be used in association with this challenge handler.
     * The login handler is used to assist in obtaining credentials to respond to challenge requests.
     *
     * @param loginHandler a login handler for credentials.
     */
    public ChallengeHandler setLoginHandler(LoginHandler loginHandler) {
        this.loginHandler = loginHandler;
        return this;
    }

    /**
     * Get the login handler associated with this challenge handler.
     * A login handler is used to assist in obtaining credentials to respond to challenge requests.
     *
     * @return a login handler to assist in providing credentials, or {@code null} if none has been established yet.
     */
    @Override
    public LoginHandler getLoginHandler() {
        return loginHandler;
    }

}
