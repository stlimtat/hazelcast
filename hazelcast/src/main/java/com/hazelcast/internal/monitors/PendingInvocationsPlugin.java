/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.monitors;

import com.hazelcast.instance.GroupProperties;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.spi.impl.operationservice.InternalOperationService;
import com.hazelcast.spi.impl.operationservice.impl.Invocation;
import com.hazelcast.spi.impl.operationservice.impl.InvocationRegistry;
import com.hazelcast.spi.impl.operationservice.impl.OperationServiceImpl;
import com.hazelcast.util.ItemCounter;

import static com.hazelcast.instance.GroupProperty.PERFORMANCE_MONITOR_PENDING_INVOCATIONS_PERIOD_SECONDS;
import static com.hazelcast.instance.GroupProperty.PERFORMANCE_MONITOR_PENDING_INVOCATIONS_THRESHOLD;

/**
 * A {@link PerformanceMonitorPlugin} that aggregates the pending invocation so that per type of operation, the occurrence
 * count is displayed.
 */
public final class PendingInvocationsPlugin extends PerformanceMonitorPlugin {

    private final InvocationRegistry invocationRegistry;
    private final ItemCounter<Class> occurrenceMap = new ItemCounter<Class>();
    private final long periodMillis;
    private final int threshold;
    private final ILogger logger;

    public PendingInvocationsPlugin(NodeEngineImpl nodeEngine) {
        InternalOperationService operationService = nodeEngine.getOperationService();
        this.invocationRegistry = ((OperationServiceImpl) operationService).getInvocationRegistry();
        this.logger = nodeEngine.getLogger(PendingInvocationsPlugin.class);
        GroupProperties props = nodeEngine.getGroupProperties();
        this.periodMillis = props.getMillis(PERFORMANCE_MONITOR_PENDING_INVOCATIONS_PERIOD_SECONDS);
        this.threshold = props.getInteger(PERFORMANCE_MONITOR_PENDING_INVOCATIONS_THRESHOLD);
    }

    @Override
    public long getPeriodMillis() {
        return periodMillis;
    }

    @Override
    public void onStart() {
        logger.info("Plugin:active: period-millis:" + periodMillis + " threshold:" + threshold);
    }

    @Override
    public void run(PerformanceLogWriter writer) {
        clean();
        scan();
        render(writer);
    }

    private void clean() {
        occurrenceMap.reset();
    }

    private void scan() {
        for (Invocation invocation : invocationRegistry) {
            occurrenceMap.add(invocation.op.getClass(), 1);
        }
    }

    private void render(PerformanceLogWriter writer) {
        writer.startSection("PendingInvocations");
        writer.writeKeyValueEntry("count", invocationRegistry.size());
        renderInvocations(writer);
        writer.endSection();
    }

    private void renderInvocations(PerformanceLogWriter writer) {
        writer.startSection("invocations");
        for (Class op : occurrenceMap.keySet()) {
            long count = occurrenceMap.get(op);
            if (count < threshold) {
                continue;
            }

            writer.writeKeyValueEntry(op.getName(), count);
        }
        writer.endSection();
    }
}
