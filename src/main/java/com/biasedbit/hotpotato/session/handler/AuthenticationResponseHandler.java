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

package com.biasedbit.hotpotato.session.handler;

import com.biasedbit.hotpotato.request.HttpRequestFuture;
import com.biasedbit.hotpotato.response.HttpResponseProcessor;
import com.biasedbit.hotpotato.session.HandlerSessionFacade;
import com.biasedbit.hotpotato.session.HttpSessionFutureListener;
import com.biasedbit.hotpotato.session.RecursiveAwareHttpRequest;
import com.biasedbit.hotpotato.util.HostPortAndUri;
import com.biasedbit.hotpotato.util.digest.DigestAuthChallenge;
import com.biasedbit.hotpotato.util.digest.DigestAuthChallengeResponse;
import com.biasedbit.hotpotato.util.digest.DigestUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.util.CharsetUtil;

import java.text.ParseException;

/**
 * Handles 401 responses.
 * <p/>
 * If authentication fails more than once or no credentials are provided, it fails the original request; otherwise it
 * creates another request bearing the AUTHORIZATION header and the provided credentials.
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class AuthenticationResponseHandler implements ResponseCodeHandler {

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
        switch (header.charAt(0)) {
            case 'b':
            case 'B':
                // Basic
                this.handleBasicAuthentication(session, initialFuture, target, request, processor);
                break;
            case 'd':
            case 'D':
                // Digest auth
                this.handleDigestAuthentication(session, initialFuture, future, target, request, processor, header);
                break;
            default:
                initialFuture.setFailure(future.getResponse(),
                                         new Throwable("Unsupported authentication scheme in header: " + header));
        }
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private <T> void handleBasicAuthentication(HandlerSessionFacade session, HttpRequestFuture<T> initialFuture,
                                               HostPortAndUri target, RecursiveAwareHttpRequest request,
                                               HttpResponseProcessor<T> processor) {

        String userAndPass = session.getUsername() + ':' + session.getPassword();
        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(userAndPass, CharsetUtil.US_ASCII);
        ChannelBuffer encoded = Base64.encode(buffer);
        request.addHeader(HttpHeaders.Names.AUTHORIZATION, encoded.toString(CharsetUtil.US_ASCII));

        HttpRequestFuture<T> nextWrapper = session.execute(target, initialFuture, request, processor);
        nextWrapper.addListener(new HttpSessionFutureListener<T>(session, nextWrapper, target, request, processor));
    }

    private <T> void handleDigestAuthentication(HandlerSessionFacade session, HttpRequestFuture<T> initialFuture,
                                                HttpRequestFuture<T> future, HostPortAndUri target,
                                                RecursiveAwareHttpRequest request, HttpResponseProcessor<T> processor,
                                                String header) {
        DigestAuthChallenge challenge;
        try {
            challenge = DigestAuthChallenge.createFromHeader(header);
        } catch (ParseException e) {
            initialFuture.setFailure(future.getResponse(), e);
            return;
        }

        String content = null;
        if (("auth-int".equals(challenge.getQop())) && (request.getContent() != null)) {
            content = request.getContent().toString(CharsetUtil.UTF_8);
        }

        DigestAuthChallengeResponse response;
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
