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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extension of {@link DefaultChannelGroup} that's used mainly as a cleanup container, where {@link #close()} is only
 * supposed to be called once.
 * <p/>
 * This is NOT synchronized (as {@link DefaultChannelGroup} is not synchronized either), so if you want to ensure
 * absolute thread safety, make sure you wrap this class with
 * {@link java.util.Collections#synchronizedSet(java.util.Set)}.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class CleanupChannelGroup extends DefaultChannelGroup {

    // internal vars --------------------------------------------------------------------------------------------------

    private final AtomicBoolean closed;

    // constructors ---------------------------------------------------------------------------------------------------

    public CleanupChannelGroup() {
        this.closed = new AtomicBoolean(false);
    }

    public CleanupChannelGroup(String name) {
        super(name);
        this.closed = new AtomicBoolean(false);
    }

    // DefaultChannelGroup --------------------------------------------------------------------------------------------

    @Override
    public ChannelGroupFuture close() {
        if (!this.closed.getAndSet(true)) {
            return super.close();
        } else {
            throw new IllegalStateException("close() already called on " + this.getClass().getSimpleName() +
                                            " with name " + this.getName());
        }
    }

    @Override
    public boolean add(Channel channel) {
        if (this.closed.get()) {
            // immediately close
            channel.close();
            return false;
        }

        return super.add(channel);
    }
}
