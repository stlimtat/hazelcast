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

package com.hazelcast.replicatedmap.impl.operation;

import com.hazelcast.nio.Address;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.replicatedmap.impl.ReplicatedMapEventPublishingService;
import com.hazelcast.replicatedmap.impl.ReplicatedMapService;
import com.hazelcast.replicatedmap.impl.record.ReplicatedRecordStore;
import com.hazelcast.spi.PartitionAwareOperation;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Puts a key to the replicated map.
 */
public class PutOperation extends AbstractReplicatedMapOperation implements PartitionAwareOperation, IdentifiedDataSerializable {

    private transient ReplicatedMapService service;
    private transient ReplicatedRecordStore store;
    private transient Data oldValue;

    public PutOperation() {
    }

    public PutOperation(String name, Data key, Data value) {
        this.name = name;
        this.key = key;
        this.value = value;
    }

    public PutOperation(String name, Data key, Data value, long ttl) {
        this.name = name;
        this.key = key;
        this.value = value;
        this.ttl = ttl;
    }

    @Override
    public void run() throws Exception {
        service = getService();
        store = service.getReplicatedRecordStore(name, true, getPartitionId());
        Address thisAddress = getNodeEngine().getThisAddress();
        boolean isLocal = getCallerAddress().equals(thisAddress);
        Object putResult = store.put(key, value, ttl, TimeUnit.MILLISECONDS, isLocal);
        this.oldValue = getNodeEngine().toData(putResult);
        response = new VersionResponsePair(putResult, store.getVersion());
        if (!isLocal) {
            sendUpdateCallerOperation(false);
        }
    }

    @Override
    public void afterRun() throws Exception {
        sendReplicationOperation(false);
        ReplicatedMapEventPublishingService eventPublishingService = service.getEventPublishingService();
        eventPublishingService.fireEntryListenerEvent(key, oldValue, value, name, getCallerAddress());
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeUTF(name);
        out.writeData(key);
        out.writeData(value);
        out.writeLong(ttl);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        name = in.readUTF();
        key = in.readData();
        value = in.readData();
        ttl = in.readLong();
    }

    @Override
    public int getFactoryId() {
        return ReplicatedMapDataSerializerHook.F_ID;
    }

    @Override
    public int getId() {
        return ReplicatedMapDataSerializerHook.OP_PUT;
    }
}