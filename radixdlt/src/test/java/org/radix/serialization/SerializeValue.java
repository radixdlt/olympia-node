package org.radix.serialization;

import static org.junit.Assume.assumeFalse;
import static org.radix.serialization.SerializationTestUtils.testEncodeDecode;

import com.radixdlt.serialization.Polymorphic;
import com.radixdlt.serialization.Serialization;
import java.util.function.Supplier;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput.Output;

public abstract class SerializeValue<T> extends RadixTest {
	protected static Serialization serialization;

	@BeforeClass
	public static void serializeObjectBeforeClass() throws Exception {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Logging.getLogger().setLevels(Logging.ALL & ~Logging.INFO & ~Logging.TRACE & ~Logging.DEBUG);

		serialization = Modules.get(Serialization.class);
	}

	private final Class<T> cls;
	private final Supplier<T> factory;

	protected SerializeValue(Class<T> cls, Supplier<T> factory) {
		this.cls = cls;
		this.factory = factory;
	}

	@Test
	public void testEncodeDecodeALL() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, serialization, Output.ALL);
	}

	@Test
	public void testEncodeDecodeAPI() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, serialization, Output.API);
	}

	@Test
	public void testEncodeDecodePERSIST() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, serialization, Output.PERSIST);
	}

	@Test
	public void testEncodeDecodeWIRE() throws Exception {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
		testEncodeDecode(factory.get(), cls, serialization, Output.WIRE);
	}
}
