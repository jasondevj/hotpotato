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

package org.factor45.hotpotato.util.digest;

import java.text.ParseException;
import java.util.Map;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class AuthChallenge {

    // internal vars --------------------------------------------------------------------------------------------------

    protected String scheme;
    protected String realm;
    protected String nonce;
    protected String algorithm;
    protected String qop;
    protected String opaque;
    protected String domain;
    protected boolean stale;

    // constructors -----------------------------------------------------------------------------------------------

    public AuthChallenge() {
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static AuthChallenge createFromHeader(String header) throws ParseException {
        AuthChallenge challenge = new AuthChallenge();
        Map<String, String> fields = DigestUtils.parseHeader(header);
        challenge.scheme = fields.get(DigestUtils.SCHEME);
        challenge.realm = fields.get(DigestUtils.REALM);
        challenge.nonce = fields.get(DigestUtils.NONCE);
        challenge.algorithm = fields.get(DigestUtils.ALGORITHM);
        challenge.qop = fields.get(DigestUtils.QOP);
        challenge.opaque = fields.get(DigestUtils.OPAQUE);
        challenge.domain = fields.get(DigestUtils.DOMAIN);
        String stale = fields.get(DigestUtils.STALE);
        if (stale != null) {
            challenge.stale = Boolean.parseBoolean(stale);
        }
        return challenge; 
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getQop() {
        return qop;
    }

    public void setQop(String qop) {
        this.qop = qop;
    }

    public String getOpaque() {
        return opaque;
    }

    public void setOpaque(String opaque) {
        this.opaque = opaque;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }
}