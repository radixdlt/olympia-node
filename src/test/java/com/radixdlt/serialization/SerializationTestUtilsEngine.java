package com.radixdlt.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flipkart.zjsonpatch.JsonDiff;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * Utilities for test encoding and decoding with serializers.
 */
final class SerializationTestUtilsEngine {

    private SerializationTestUtilsEngine() {
        throw new IllegalStateException("Can't construct");
    }

    static <T> void testEncodeDecodeJson(T target, Class<T> cls, Serialization serialization, DsonOutput.Output output)
    	throws IOException {
        String json1 = serialization.toJson(target, output);
        T newTarget = serialization.fromJson(json1, cls);
        String json2 = serialization.toJson(newTarget, output);
        compareJson(json1, json2);
    }

    static <T> void testEncodeDecodeDson(T target, Class<T> cls, Serialization serialization, DsonOutput.Output output)
		throws IOException {
        byte[] dson1 = serialization.toDson(target, output);
        T newTarget = serialization.fromDson(dson1, cls);
        byte[] dson2 = serialization.toDson(newTarget, output);
        compareDson(dson1, dson2);
    }

    static void compareDson(byte[] dson1, byte[] dson2) {
    	assertArrayEquals(dson1, dson2);
    }

    static void compareJson(String s1Json, String s2aJson, String s2bJson) throws IOException {
        compareJson(s1Json, s2aJson);
        compareJson(s2aJson, s2bJson);
    }

    static void compareJson(String s1Json, String s2Json) throws IOException {
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        om.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        JsonNode s1Tree = om.readTree(s1Json);
        JsonNode s2Tree = om.readTree(s2Json);
        if (!s1Tree.equals(s2Tree)) {
            JsonNode patch12 = JsonDiff.asJson(s1Tree, s2Tree);
            JsonNode patch21 = JsonDiff.asJson(s2Tree, s1Tree);
            fail(String.format("Not equivalent JSON:%n%s%n%s%n%s%n%s",
                    toSortedString(om, s1Tree), toSortedString(om, s2Tree),
                    patch12.toString(), patch21.toString()));
        }
    }

    static String toSortedString(ObjectMapper om, JsonNode node) throws JsonProcessingException {
        final Object obj = om.treeToValue(node, Object.class);
        return om.writeValueAsString(obj);
    }
}
