package com.radixdlt.serialization;

import org.junit.Test;

import java.util.function.Supplier;

import static com.radixdlt.serialization.SerializationTestUtilsEngine.testEncodeDecodeJson;
import static com.radixdlt.serialization.SerializationTestUtilsEngine.testEncodeDecodeDson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

/**
 * Raft of tests for serialization of objects.
 * <p>
 * Note that the tests that round-trip types through the serializer
 * are not run for {@link Polymorphic} types, as these types do not
 * serialize to themselves, but to one of their superclasses.
 *
 * @param <T> The type under test.
 */
public abstract class SerializeObjectEngine<T> {

    private final Class<T> cls;
    private final Supplier<T> factory;
    private final Serialization serialization = Serialization.getDefault();

    protected SerializeObjectEngine(Class<T> cls, Supplier<T> factory) {
        this.cls = cls;
        this.factory = factory;
    }

    @Test
    public void testNONEIsEmpty() throws Exception {
        String s2Json = this.serialization.toJson(factory.get(), DsonOutput.Output.NONE);
        assertEquals("{}", s2Json);

        byte[] s2Dson = this.serialization.toDson(factory.get(), DsonOutput.Output.NONE);
        // CBOR should be completely empty, or empty map
        if (s2Dson.length != 0) {
        	assertEquals(2, s2Dson.length);
        	assertEquals(0xBF, s2Dson[0] & 0xFF); // Indeterminate length map
        	assertEquals(0xFF, s2Dson[1] & 0xFF); // End of map
        }
    }

    @Test
    public void testEncodeDecodeALL() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecodeJson(factory.get(), cls, this.serialization, DsonOutput.Output.ALL);
        testEncodeDecodeDson(factory.get(), cls, this.serialization, DsonOutput.Output.ALL);
    }

    @Test
    public void testEncodeDecodeAPI() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecodeJson(factory.get(), cls, this.serialization, DsonOutput.Output.API);
        testEncodeDecodeDson(factory.get(), cls, this.serialization, DsonOutput.Output.API);
    }

    @Test
    public void testEncodeDecodePERSIST() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecodeJson(factory.get(), cls, this.serialization, DsonOutput.Output.PERSIST);
        testEncodeDecodeDson(factory.get(), cls, this.serialization, DsonOutput.Output.PERSIST);
    }

    @Test
    public void testEncodeDecodeWIRE() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecodeJson(factory.get(), cls, this.serialization, DsonOutput.Output.WIRE);
        testEncodeDecodeDson(factory.get(), cls, this.serialization, DsonOutput.Output.WIRE);
    }
}
