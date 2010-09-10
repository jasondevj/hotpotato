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

import org.factor45.hotpotato.util.TextUtils;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class DigestUtils {

    // common authentication properties
    public static final String SCHEME = "scheme";
    public static final String RESPONSE = "response";
    public static final String USERNAME = "username";
    public static final String REALM = "realm";
    public static final String NONCE = "nonce";
    public static final String URI = "uri";
    public static final String ALGORITHM = "algorithm";
    public static final String QOP = "qop";
    public static final String NONCE_COUNT = "nc";
    public static final String CLIENT_NONCE = "cnonce";
    public static final String OPAQUE = "opaque";
    public static final String DOMAIN = "domain";
    public static final String STALE = "stale";

    // TODO validate this REGEX against RFC
    //private static final Pattern PROPERTY_PATTERN = Pattern.compile("(\\w+)=(\"?)([\\w@\\.\\-=]+)(\"?)");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("(\\w+)=(\"?)([^\"]+)(\"?)");

    // public static methods ------------------------------------------------------------------------------------------

    public static Map<String, String> parseHeader(String header) throws ParseException {
        // find first space - not using regex's here because I then need to extract the substring
        // starting after scheme to end to apply a regex to isolate the parameters.
        int firstSpace = header.indexOf(" ");
        if (firstSpace < 0) {
            throw new ParseException("Invalid header content: expected whitespace after scheme not found", 0);
        }
        Map<String, String> response = new HashMap<String, String>();
        response.put(SCHEME, header.substring(0, firstSpace));

        String[] params = header.substring(firstSpace + 1).split(",");
        if (params.length < 4) {
            throw new ParseException("Invalid header content: username, response, nonce & uri are required", 0);
        }
        for (String param : params) {
            param = param.trim();
            Matcher m = PROPERTY_PATTERN.matcher(param);
            if (m.find()) {
                response.put(m.group(1), m.group(3));
            }
        }
        return response;
    }

    public static AuthChallengeResponse computeResponse(AuthChallenge challenge, String method, String content,
                                                        String uri, String username, String password, int nonceCount)
            throws ParseException {

        String realm = challenge.getRealm();
        String nonce = challenge.getNonce();
        String cnonce = null;

        String qop = challenge.getQop();
        boolean hasQop = (qop != null) && (qop.length() > 0);
        // TODO fix this to stop being static
        String nc = toNonceCount(nonceCount);

        String ha1 = TextUtils.hash(
                new StringBuilder().append(username).append(":").append(realm).append(":").append(password).toString());
        String ha2;
        if ("auth-int".equals(qop)) {
            String entityBody = (content == null ? "" : content);
            ha2 = TextUtils.hash(new StringBuilder()
                    .append(method).append(":")
                    .append(uri).append(":")
                    .append(TextUtils.hash(entityBody)).toString());
        } else {
            ha2 = TextUtils.hash(new StringBuilder().append(method).append(":").append(uri).toString());
        }
        String hashedResponse;
        if (hasQop) {
            cnonce = TextUtils.hash(username);
            hashedResponse = TextUtils.hash(new StringBuilder()
                    .append(ha1).append(":")
                    .append(nonce).append(":")
                    .append(nc).append(":")
                    .append(cnonce).append(":")
                    .append(qop).append(":")
                    .append(ha2).toString());
        } else {
            // Backwards compatibility with RFC 2069
            hashedResponse = TextUtils.hash(new StringBuilder()
                    .append(ha1).append(":")
                    .append(nonce).append(":")
                    .append(ha2).toString());
        }

        AuthChallengeResponse authResponse = new AuthChallengeResponse();
        authResponse.setScheme(challenge.getScheme());
        authResponse.setUsername(username);
        authResponse.setNonce(challenge.getNonce());
        authResponse.setUri(uri);
        authResponse.setResponse(hashedResponse);
        if (challenge.getRealm() != null) {
            authResponse.setRealm(challenge.getRealm());
        }
        if (challenge.getAlgorithm() != null) {
            authResponse.setAlgorithm(challenge.getAlgorithm());
        } else {
            authResponse.setAlgorithm("MD5");
        }
        if (hasQop) {
            authResponse.setQop(challenge.getQop());
            authResponse.setCnonce(cnonce);
            authResponse.setNonceCount(nonceCount);
        }

        return authResponse;
    }

    public static boolean validResponse(AuthChallenge challenge, AuthChallengeResponse response) {
        return false;
    }

    public static String toNonceCount(int nonceCount) {
        String hexString = Integer.toHexString(nonceCount);
        int l = hexString.length();
        if (l >= 8) {
            return hexString;
        }

        return TextUtils.repeat("0", (8 - l)) + hexString;
    }
}
