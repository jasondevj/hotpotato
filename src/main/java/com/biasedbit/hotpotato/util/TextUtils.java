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

package com.biasedbit.hotpotato.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Text utility methods.
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class TextUtils {

    // constructors ---------------------------------------------------------------------------------------------------

    private TextUtils() {

    }

    // public static methods ------------------------------------------------------------------------------------------
    
    /**
     * Hash a string
     *
     * @param toHash String to be hashed.
     *
     * @return Hashed string.
     */
    public static String hash(Object toHash) {
        String hashString = toHash.toString();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return hashString;
        }

        md.update(hashString.getBytes(), 0, hashString.length());
        return convertToHex(md.digest());
    }

    public static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (byte aData : data) {
            int halfbyte = (aData >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9)) {
                    buf.append((char) ('0' + halfbyte));
                } else {
                    buf.append((char) ('a' + (halfbyte - 10)));
                }
                halfbyte = aData & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    /**
     * Returns a string with a given characted repeated n times.
     *
     * @param str String.
     * @param times Number of repetitions.
     *
     * @return String with <code>times</code> repetitions of <code>str</code>
     */
    public static String repeat(Object str, int times) {
        StringBuilder builder = new StringBuilder(str.toString());
        for (int i = 1; i < times; i++) {
            builder.append(str);
        }

        return builder.toString();
    }
}
