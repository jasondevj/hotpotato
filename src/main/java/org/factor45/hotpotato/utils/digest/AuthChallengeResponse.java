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

/*
 * Copyright (c) 2009 WIT Software. All rights reserved.
 *
 * WIT Software Confidential and Proprietary information. It is strictly forbidden for 3rd parties to modify, decompile,
 * disassemble, defeat, disable or circumvent any protection mechanism; to sell, license, lease, rent, redistribute or
 * make accessible to any third party, whether for profit or without charge.
 *
 * carvalho 2009/06/03
 */

package org.factor45.hotpotato.utils.digest;

import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:bruno.carvalho@wit-software.com">Bruno de Carvalho</a>
 */
public class AuthChallengeResponse {

    // internal vars
    private final Map<String, String> properties;

    // constructors ---------------------------------------------------------------------------------------------------

    public AuthChallengeResponse(Map<String, String> properties) {
        this.properties = properties;
    }

    public AuthChallengeResponse() {
        this.properties = new HashMap<String, String>();
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static AuthChallengeResponse createFromHeader(String header) throws ParseException {
        return new AuthChallengeResponse(DigestUtils.parseHeader(header));
    }

    public static boolean validateHeaderContent(AuthChallengeResponse response) {
        return ((response.properties.get(DigestUtils.SCHEME) != null) &&
                (response.properties.get(DigestUtils.RESPONSE) != null) &&
                (response.properties.get(DigestUtils.NONCE) == null) &&
                (response.properties.get(DigestUtils.USERNAME) == null) &&
                (response.properties.get(DigestUtils.URI) != null));
    }

    public String buildAsString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getScheme())
                .append(" username=\"").append((this.getUsername()))
                .append("\", nonce=\"").append(this.getNonce())
                .append("\", uri=\"").append(this.getUri())
                .append("\", response=\"").append(this.getResponse()).append('\"');
        String tmp = this.getRealm();
        if (tmp != null) {
            builder.append(", realm=\"").append(tmp).append('\"');
        }
        tmp = this.getAlgorithm();
        if (tmp != null) {
            builder.append(", algorithm=").append(tmp);
        }
        tmp = this.getQop();
        if (tmp != null) {
            builder.append(", qop=").append(tmp)
                    .append(", cnonce=\"").append(this.getCnonce())
                    .append("\", nc=").append(this.getNonceCount());
        }

        return builder.toString();
    }

    public String getProperty(String key) {
        return this.properties.get(key);
    }

    public void setProperty(String key, String value) {
        this.properties.put(key, value);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public String getScheme() {
        return this.properties.get(DigestUtils.SCHEME);
    }

    public void setScheme(String scheme) {
        this.properties.put(DigestUtils.SCHEME, scheme);
    }

    public String getResponse() {
        return this.properties.get(DigestUtils.RESPONSE);
    }

    public void setResponse(String response) {
        this.properties.put(DigestUtils.RESPONSE, response);
    }

    public String getRealm() {
        return this.properties.get(DigestUtils.REALM);
    }

    public void setRealm(String realm) {
        this.properties.put(DigestUtils.REALM, realm);
    }

    public String getNonce() {
        return this.properties.get(DigestUtils.NONCE);
    }

    public void setNonce(String nonce) {
        this.properties.put(DigestUtils.NONCE, nonce);
    }

    public String getAlgorithm() {
        return this.properties.get(DigestUtils.ALGORITHM);
    }

    public void setAlgorithm(String algorithm) {
        this.properties.put(DigestUtils.ALGORITHM, algorithm);
    }

    public String getUsername() {
        return this.properties.get(DigestUtils.USERNAME);
    }

    public void setUsername(String username) {
        this.properties.put(DigestUtils.USERNAME, username);
    }

    public String getUri() {
        return this.properties.get(DigestUtils.URI);
    }

    public void setUri(String uri) {
        this.properties.put(DigestUtils.URI, uri);
    }

    public String getQop() {
        return this.properties.get(DigestUtils.QOP);
    }

    public void setQop(String qop) {
        this.properties.put(DigestUtils.QOP, qop);
    }

    public String getNonceCount() {
        return this.properties.get(DigestUtils.NONCE_COUNT);
    }

    public void setNonceCount(int nonceCount) {
        this.properties.put(DigestUtils.NONCE_COUNT, DigestUtils.toNonceCount(nonceCount));
    }

    public String getCnonce() {
        return this.properties.get(DigestUtils.CLIENT_NONCE);
    }

    public void setCnonce(String cnonce) {
        this.properties.put(DigestUtils.CLIENT_NONCE, cnonce);
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(this.properties);
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("AuthChallengeResponse{")
                .append("properties=").append(this.properties)
                .append('}').toString();
    }
}
