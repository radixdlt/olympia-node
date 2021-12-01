package com.radixdlt.store.tree.storage;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class InMemoryPMTStorageTest {

    @Test
    public void when_key_value_has_been_inserted__then_it_can_be_retrieved() {
        // given
        PMTStorage inMemoryPMTStorage = new InMemoryPMTStorage();
        String key = "key";
        String value = "value";
        inMemoryPMTStorage.save(
                key.getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8)
        );

        // when
        byte[] retrievedValue = inMemoryPMTStorage.read(key.getBytes(StandardCharsets.UTF_8));

        // then
        assertArrayEquals(
                value.getBytes(StandardCharsets.UTF_8),
                retrievedValue
        );
    }
    @Test
    public void when_key_value_has_not_been_inserted__then_null_is_returned_in_retrieval() {
        // given
        PMTStorage inMemoryPMTStorage = new InMemoryPMTStorage();

        // when
        byte[] value = inMemoryPMTStorage.read("key".getBytes(StandardCharsets.UTF_8));

        // then
        assertNull(value);
    }
}
