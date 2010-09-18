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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * As seen on <a href="http://bruno.factor45.org/blag/2010/06/28/naming-threads-created-with-the-executorservice/">
 * TV</a>.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class NamedThreadFactory implements ThreadFactory {

    // constants -----------------------------------------------------------------

    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

    // internal vars -------------------------------------------------------------

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    // constructors --------------------------------------------------------------

    public NamedThreadFactory() {
        this("ThreadPool(" + POOL_NUMBER.getAndIncrement() + "-thread-");
    }

    public NamedThreadFactory(String namePrefix) {
        SecurityManager s = System.getSecurityManager();
        this.group = (s != null) ? s.getThreadGroup() :
                     Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix + "(pool" +
                          POOL_NUMBER.getAndIncrement() + "-thread-";
    }

    // ThreadFactory -------------------------------------------------------------

    public Thread newThread(Runnable r) {
        Thread t = new Thread(this.group, r, this.namePrefix +
                              this.threadNumber.getAndIncrement() + ")", 0L);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}