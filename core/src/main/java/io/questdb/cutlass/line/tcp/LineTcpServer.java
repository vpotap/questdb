/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.cutlass.line.tcp;

import java.io.Closeable;

import org.jetbrains.annotations.Nullable;

import io.questdb.WorkerPoolAwareConfiguration;
import io.questdb.cairo.CairoEngine;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import io.questdb.mp.EagerThreadSetup;
import io.questdb.mp.WorkerPool;
import io.questdb.network.IOContextFactory;
import io.questdb.network.IODispatcher;
import io.questdb.network.IODispatchers;
import io.questdb.std.Misc;
import io.questdb.std.ObjList;
import io.questdb.std.ObjectFactory;
import io.questdb.std.ThreadLocal;
import io.questdb.std.WeakObjectPool;

public class LineTcpServer implements Closeable {
    private static final Log LOG = LogFactory.getLog(LineTcpServer.class);
    private final IODispatcher<LineTcpConnectionContext> dispatcher;
    private final LineTcpConnectionContextFactory contextFactory;
    private final LineTcpMeasurementScheduler scheduler;
    private final ObjList<WorkerPool> dedicatedPools;

    public LineTcpServer(
            LineTcpReceiverConfiguration lineConfiguration,
            CairoEngine engine,
            WorkerPool ioWorkerPool,
            WorkerPool writerWorkerPool,
            ObjList<WorkerPool> dedicatedPools
    ) {
        this.contextFactory = new LineTcpConnectionContextFactory(lineConfiguration);
        this.dispatcher = IODispatchers.create(
                lineConfiguration.getNetDispatcherConfiguration(),
                contextFactory);
        this.dedicatedPools = dedicatedPools;
        ioWorkerPool.assign(dispatcher);
        int nIOWorkers = ioWorkerPool.getWorkerCount();
        scheduler = new LineTcpMeasurementScheduler(lineConfiguration, engine, writerWorkerPool, nIOWorkers);
        for (int i = 0; i < nIOWorkers; i++) {
            final int j = i;
            ioWorkerPool.assign(i, scheduler.new NetworkIOJobImpl(dispatcher, j));
        }

        final Closeable cleaner = contextFactory::closeContextPool;
        for (int i = 0, n = ioWorkerPool.getWorkerCount(); i < n; i++) {
            // http context factory has thread local pools
            // therefore we need each thread to clean their thread locals individually
            ioWorkerPool.assign(i, cleaner);
        }
    }

    @Nullable
    public static LineTcpServer create(
            LineTcpReceiverConfiguration lineConfiguration,
            WorkerPool sharedWorkerPool,
            Log log,
            CairoEngine cairoEngine
    ) {
        if (!lineConfiguration.isEnabled()) {
            return null;
        }

        ObjList<WorkerPool> dedicatedPools = new ObjList<>(2);
        WorkerPool ioWorkerPool = WorkerPoolAwareConfiguration.configureWorkerPool(lineConfiguration.getIOWorkerPoolConfiguration(), sharedWorkerPool);
        WorkerPool writerWorkerPool = WorkerPoolAwareConfiguration.configureWorkerPool(lineConfiguration.getWriterWorkerPoolConfiguration(), sharedWorkerPool);
        if (ioWorkerPool != sharedWorkerPool) {
            dedicatedPools.add(ioWorkerPool);
        }
        if (writerWorkerPool != sharedWorkerPool) {
            dedicatedPools.add(writerWorkerPool);
        }
        LineTcpServer lineTcpServer = new LineTcpServer(lineConfiguration, cairoEngine, ioWorkerPool, writerWorkerPool, dedicatedPools);
        if (ioWorkerPool != sharedWorkerPool) {
            ioWorkerPool.start(LOG);
        }
        if (writerWorkerPool != sharedWorkerPool) {
            writerWorkerPool.start(LOG);
        }
        return lineTcpServer;
    }

    @Override
    public void close() {
        for (int n = 0, sz = dedicatedPools.size(); n < sz; n++) {
            dedicatedPools.get(n).halt();
        }
        Misc.free(scheduler);
        Misc.free(contextFactory);
        Misc.free(dispatcher);
    }

    private class LineTcpConnectionContextFactory implements IOContextFactory<LineTcpConnectionContext>, Closeable, EagerThreadSetup {
        private final ThreadLocal<WeakObjectPool<LineTcpConnectionContext>> contextPool;
        private boolean closed = false;

        public LineTcpConnectionContextFactory(LineTcpReceiverConfiguration configuration) {
            ObjectFactory<LineTcpConnectionContext> factory;
            if (null == configuration.getAuthDbPath()) {
                if (configuration.isIOAggressiveRecv()) {
                    factory = () -> new AggressiveRecvLineTcpConnectionContext(configuration, scheduler);
                } else {
                    factory = () -> new LineTcpConnectionContext(configuration, scheduler);
                }
            } else {
                AuthDb authDb = new AuthDb(configuration);
                factory = () -> new LineTcpAuthConnectionContext(configuration, authDb, scheduler);
            }

            this.contextPool = new ThreadLocal<>(() -> new WeakObjectPool<>(factory, configuration.getConnectionPoolInitialCapacity()));
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public LineTcpConnectionContext newInstance(long fd, IODispatcher<LineTcpConnectionContext> dispatcher) {
            return contextPool.get().pop().of(fd, dispatcher);
        }

        @Override
        public void done(LineTcpConnectionContext context) {
            if (closed) {
                Misc.free(context);
            } else {
                context.of(-1, null);
                contextPool.get().push(context);
                LOG.debug().$("pushed").$();
            }
        }

        @Override
        public void setup() {
            contextPool.get();
        }

        private void closeContextPool() {
            Misc.free(this.contextPool.get());
            LOG.info().$("closed").$();
        }
    }
}
