package com.radixdlt.store.tree.hash;

import com.radixdlt.crypto.HashUtils;

public class SHA256 implements HashFunction {
    @Override
    public byte[] hash(byte[] data) {
        return HashUtils.sha256(data).asBytes();
    }
}
