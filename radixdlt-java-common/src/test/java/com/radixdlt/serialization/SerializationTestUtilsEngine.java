package com.radixdlt.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * Utilities for test encoding and decoding with serializers.
 */
final class SerializationTestUtilsEngine {

    private SerializationTestUtilsEngine() {
        throw new IllegalStateException("Can't construct");
    }

    static <T> void testEncodeDecode(T target, Class<T> cls, Serialization serialization, DsonOutput.Output output)
            throws Exception {
        String json1 = serialization.toJson(target, output);
        T newTarget = serialization.fromJson(json1, cls);
        String json2 = serialization.toJson(newTarget, output);
        compareJson(json1, json2);
    }

    static void compareJson(String s1Json, String s2Json) throws IOException {
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        om.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        JsonNode s1Tree = om.readTree(s1Json);
        JsonNode s2Tree = om.readTree(s2Json);
        if (!s1Tree.equals(s2Tree)) {
            fail("Not equivalent JSON");
        }
    }
}
