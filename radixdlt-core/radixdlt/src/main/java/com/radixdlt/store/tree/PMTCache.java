package com.radixdlt.store.tree;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.time.Duration;
import java.util.function.Function;

public class PMTCache {

    private final LoadingCache<byte[], PMTNode> cache;

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
                    public PMTNode load(byte[] key) {
                        return loader.apply(key);
                    }
                }
            );
    }

    public void put(byte[] key, PMTNode node) {
        this.cache.put(key, node);
    }

    public PMTNode get(byte[] key) {
        return this.cache.getUnchecked(key);
    }
}
