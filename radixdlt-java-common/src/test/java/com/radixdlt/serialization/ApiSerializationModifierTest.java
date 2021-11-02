package com.radixdlt.serialization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiSerializationModifierTest {

    @SerializeWithHid
    private static final class DummyWithHid {
        @JsonProperty
        public int getDummy() {
            return 2;
        }
    }

    private static final class DummyWithoutHid {
        @JsonProperty
        public int getDummy() {
            return 2;
        }
    }

    private Serialization serialization;

    @Before
    public void setup() {
        serialization = Serialization.create(ClasspathScanningSerializerIds.create(), ClasspathScanningSerializationPolicy.create());
    }

    @Test
    public void testIncludeHidFieldInDson() throws DeserializeException {
        byte[] output = serialization.toDson(new DummyWithHid(), DsonOutput.Output.API);
        JSONObject obj = serialization.fromDson(output, JSONObject.class);
        assertTrue(obj.has("hid"));
    }

    @Test
    public void testDontIncludeHidFieldInDson() throws DeserializeException {
        byte[] output = serialization.toDson(new DummyWithoutHid(), DsonOutput.Output.API);
        JSONObject obj = serialization.fromDson(output, JSONObject.class);
        assertFalse(obj.has("hid"));
    }
}
