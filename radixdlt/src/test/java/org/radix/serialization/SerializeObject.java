package org.radix.serialization;

import com.radixdlt.serialization.Polymorphic;

import java.util.function.Supplier;

import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.logging.Logging;
import com.radixdlt.serialization.DsonOutput.Output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.radix.serialization.SerializationTestUtils.testEncodeDecode;

/**
 * Raft of tests for serialization of objects.
 * <p>
 * Note that the tests that round-trip types through the serializer
 * are not run for {@link Polymorphic} types, as these types do not
 * serialize to themselves, but to one of their superclasses.
 *
 * @param <T> The type under test.
 */
public abstract class SerializeObject<T> extends RadixTest {
	@BeforeClass
	public static void serializeObjectBeforeClass() throws Exception {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Logging.getLogger().setLevels(Logging.ALL & ~Logging.INFO & ~Logging.TRACE & ~Logging.DEBUG);
	}

	private final Class<T> cls;
	private final Supplier<T> factory;

	protected SerializeObject(Class<T> cls, Supplier<T> factory) {
		this.cls = cls;
		this.factory = factory;
	}

	@Test
	public void testNONEIsEmpty() throws Exception {
		String s2Json = getSerialization().toJson(factory.get(), Output.NONE);
		assertEquals("{}", s2Json);
	}

	@Test
	public void testEncodeDecodeALL() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.ALL);
	}

	@Test
	public void testEncodeDecodeAPI() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.API);
	}

//	@Test
//	public void testEncodeDecodeHASH() throws Exception {
//		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
//		// Output.HASH does not serialize "serializers" and can't be deserialized
//	}

//	@Test
//	public void testEncodeDecodeNONE() throws Exception {
//		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
//		// Output.NONE does not serialize "serializers" and can't be deserialized
//	}

	@Test
	public void testEncodeDecodePERSIST() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.PERSIST);
	}

	@Test
	public void testEncodeDecodeWIRE() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.WIRE);
	}
}
