package com.radixdlt.store.tree.storage;

import com.google.common.cache.CacheLoader;
import com.radixdlt.store.tree.PMTKey;
import com.radixdlt.store.tree.PMTLeaf;
import com.radixdlt.store.tree.PMTNode;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class PMTCacheTest {

    @Test
    public void when_key_value_has_been_inserted_and_has_not_expired__then_it_can_be_retrieved_without_loading() {
        // given
        PMTCache pmtCache = new PMTCache(
                1,
                Duration.of(1, ChronoUnit.MINUTES),
                key -> {
                    throw new IllegalStateException();
                }
        );

        String key = "key";
        String value = "value";

        // when
        pmtCache.put(
                key.getBytes(StandardCharsets.UTF_8),
                new PMTLeaf(
                        new PMTKey(key.getBytes(StandardCharsets.UTF_8)),
                        value.getBytes(StandardCharsets.UTF_8)
                )
        );

        // then
        PMTNode pmtNode = pmtCache.get(key.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(
            new PMTLeaf(
                    new PMTKey(key.getBytes(StandardCharsets.UTF_8)),
                    value.getBytes(StandardCharsets.UTF_8)
            ),
            pmtNode
        );
    }

    @Test
    public void when_key_value_has_not_been_inserted_and_cannot_be_load__then_exception_is_thrown() {
        // given
        PMTCache pmtCache = new PMTCache(
                1,
                Duration.of(1, ChronoUnit.MINUTES),
                key -> null
        );

        // when - then
        Assert.assertThrows(
                CacheLoader.InvalidCacheLoadException.class,
                () -> pmtCache.get("key".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    public void when_key_value_has_been_inserted_and_has_expired__then_it_can_be_loaded_and_retrieved() {
        // given
        String key = "key";
        String value = "value";
        PMTCache pmtCache = new PMTCache(
                1,
                Duration.of(1, ChronoUnit.NANOS),
                k -> new PMTLeaf(
                        new PMTKey(key.getBytes(StandardCharsets.UTF_8)),
                        value.getBytes(StandardCharsets.UTF_8)
                )
        );

        pmtCache.put(
                key.getBytes(StandardCharsets.UTF_8),
                new PMTLeaf(
                        new PMTKey(key.getBytes(StandardCharsets.UTF_8)),
                        value.getBytes(StandardCharsets.UTF_8)
                )
        );

        // when
        PMTNode pmtNode = pmtCache.get(key.getBytes(StandardCharsets.UTF_8));

        // then
        Assert.assertEquals(
                new PMTLeaf(
                        new PMTKey(key.getBytes(StandardCharsets.UTF_8)),
                        value.getBytes(StandardCharsets.UTF_8)
                ),
                pmtNode
        );
    }
}
