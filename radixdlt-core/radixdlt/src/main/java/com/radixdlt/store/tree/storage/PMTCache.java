package com.radixdlt.store.tree.storage;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.radixdlt.store.tree.PMTNode;

import java.time.Duration;
import java.util.function.Function;

public class PMTCache {

    private final LoadingCache<ByteArrayWrapper, PMTNode> cache;

    public PMTCache(
        int maximumSize,
        Duration expireAfter,
        Function<byte[], ? extends PMTNode> loader
    ) {
        this.cache = CacheBuilder.newBuilder()
            .expireAfterAccess(expireAfter)
            .maximumSize(maximumSize)
            .build(
                new CacheLoader<>() {
                    @Override
                    public PMTNode load(ByteArrayWrapper key) {
                        return loader.apply(key.getData());
                    }
                }
            );
    }

    public void put(byte[] key, PMTNode node) {
        this.cache.put(ByteArrayWrapper.from(key), node);
    }

    public PMTNode get(byte[] key) {
        return this.cache.getUnchecked(ByteArrayWrapper.from(key));
    }
}
