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

package org.factor45.hotpotato.session.handler;

import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.response.HttpResponseProcessor;
import org.factor45.hotpotato.session.HandlerSessionFacade;
import org.factor45.hotpotato.session.HttpSession;
import org.factor45.hotpotato.session.HttpSessionFutureListener;
import org.factor45.hotpotato.session.RecursiveAwareHttpRequest;
import org.factor45.hotpotato.util.HostPortAndUri;
import org.factor45.hotpotato.util.digest.AuthChallenge;
import org.factor45.hotpotato.util.digest.AuthChallengeResponse;
import org.factor45.hotpotato.util.digest.DigestUtils;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.util.CharsetUtil;

import java.text.ParseException;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class AuthorisationResponseHandler implements ResponseCodeHandler {

    // ResponseCodeHandler --------------------------------------------------------------------------------------------

    @Override
    public int[] handlesResponseCodes() {
        return new int[]{401};
    }

    @Override
    public <T> void handleResponse(HandlerSessionFacade session, HttpRequestFuture<T> initialFuture,
                                   HttpRequestFuture<T> future, HostPortAndUri target,
                                   RecursiveAwareHttpRequest request, HttpResponseProcessor<T> processor) {
        if (request.isFailedAuth()) {
            // Do not retry auth.
            initialFuture.setSuccess(future.getProcessedResult(), future.getResponse());
            return;
        }

        if (session.getUsername() == null) {
            // "Fail" immediately, because we have no credentials
            initialFuture.setSuccess(future.getProcessedResult(), future.getResponse());
            return;
        }

        // Mark auth as failed
        request.failedAuth();

        String header = future.getResponse().getHeader(HttpHeaders.Names.WWW_AUTHENTICATE);
        AuthChallenge challenge;
        try {
            challenge = AuthChallenge.createFromHeader(header);
        } catch (ParseException e) {
            initialFuture.setFailure(future.getResponse(), e);
            return;
        }

        String content = null;
        if (("auth-int".equals(challenge.getQop())) && (request.getContent() != null)) {
            content = request.getContent().toString(CharsetUtil.UTF_8);
        }

        AuthChallengeResponse response;
        try {
            response = DigestUtils.computeResponse(challenge, request.getMethod().toString(), content, request.getUri(),
                                                   session.getUsername(), session.getPassword(), 1);
        } catch (ParseException e) {
            initialFuture.setFailure(future.getResponse(), e);
            return;
        }

        request.addHeader(HttpHeaders.Names.AUTHORIZATION, response.buildAsString());

        HttpRequestFuture<T> nextWrapper = session.execute(target, initialFuture, request, processor);
        nextWrapper.addListener(new HttpSessionFutureListener<T>(session, nextWrapper, target, request, processor));
    }
}
