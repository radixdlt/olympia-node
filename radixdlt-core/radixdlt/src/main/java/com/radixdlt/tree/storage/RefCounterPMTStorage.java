package com.radixdlt.tree.storage;

import java.util.Arrays;

public class RefCounterPMTStorage implements PMTStorage{

    private final PMTStorage pmtStorage;

    public RefCounterPMTStorage(PMTStorage pmtStorage) {
        this.pmtStorage = pmtStorage;
    }

    @Override
    public void save(byte[] serialisedNodeHash, byte[] serialisedNode) {
        byte[] newData = this.pmtStorage.read(serialisedNodeHash);
        if (newData != null) {
            newData[serialisedNode.length]++;
        } else {
            newData = new byte[serialisedNode.length + 1];
            System.arraycopy(serialisedNode, 0, newData, 0, serialisedNode.length);
            newData[serialisedNode.length] = 1;
        }
        this.pmtStorage.save(serialisedNodeHash, newData);
    }

    @Override
    public byte[] read(byte[] serialisedNodeHash) {
        byte[] bytes = this.pmtStorage.read(serialisedNodeHash);
        return Arrays.copyOfRange(bytes, 0, bytes.length - 1);
    }

    @Override
    public void delete(byte[] serialisedNodeHash) {
        byte[] existingData = this.pmtStorage.read(serialisedNodeHash);

        if (existingData[existingData.length - 1] == 1) {
            this.pmtStorage.delete(serialisedNodeHash);
        } else {
            existingData[existingData.length - 1]--;
            this.pmtStorage.save(serialisedNodeHash, existingData);
        }
    }
}
