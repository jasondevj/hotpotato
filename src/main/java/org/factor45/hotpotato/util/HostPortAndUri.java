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

package org.factor45.hotpotato.util;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class HostPortAndUri {

    // internal vars --------------------------------------------------------------------------------------------------

    private String scheme;
    private String host;
    private int port;
    private String uri;

    // constructors ---------------------------------------------------------------------------------------------------

    public HostPortAndUri(String scheme, String host, int port, String uri) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.uri = uri;
    }

    public HostPortAndUri(HostPortAndUri that) {
        this.scheme = that.scheme;
        this.host = that.host;
        this.port = that.port;
        this.uri = that.uri;
    }

    // public methods -------------------------------------------------------------------------------------------------

    public String asUrl() {
        return this.scheme + "://" + this.host + ":" + this.port + this.uri;
    }

    public boolean isHttps() {
        return this.scheme.equals("https");
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder().append("HostPortAndUri{").append(host).append(':').append(port)
                .append(uri == null ? "" : uri).append('}').toString();
    }
}
