package com.radixdlt.store.tree.hash;

@FunctionalInterface
public interface HashFunction {

    byte[] hash(byte[] data);
}
