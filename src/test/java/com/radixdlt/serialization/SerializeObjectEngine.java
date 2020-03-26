package com.radixdlt.serialization;

import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import org.junit.Test;

import java.util.function.Supplier;

import static com.radixdlt.serialization.SerializationTestUtilsEngine.testEncodeDecode;
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
    private final Serialization serialization = Serialization.create(
            ClasspathScanningSerializerIds.create(),
            ClasspathScanningSerializationPolicy.create()
    );


    protected SerializeObjectEngine(Class<T> cls, Supplier<T> factory) {
        this.cls = cls;
        this.factory = factory;
    }

    @Test
    public void testNONEIsEmpty() throws Exception {
        String s2Json = this.serialization.toJson(factory.get(), DsonOutput.Output.NONE);
        assertEquals("{}", s2Json);
    }

    @Test
    public void testEncodeDecodeALL() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.ALL);
    }

    @Test
    public void testEncodeDecodeAPI() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.API);
    }

    @Test
    public void testEncodeDecodePERSIST() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.PERSIST);
    }

    @Test
    public void testEncodeDecodeWIRE() throws Exception {
        assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
        testEncodeDecode(factory.get(), cls, this.serialization, DsonOutput.Output.WIRE);
    }
}
