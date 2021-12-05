package com.radixdlt.store.tree.hash;

import com.radixdlt.crypto.HashUtils;

public class Keccak256 implements HashFunction {
    @Override
    public byte[] hash(byte[] data) {
        return HashUtils.kec256(data);
    }
}
