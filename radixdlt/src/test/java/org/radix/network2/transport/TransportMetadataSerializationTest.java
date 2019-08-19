package org.radix.network2.transport;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import static org.junit.Assert.assertEquals;

public class TransportMetadataSerializationTest {

	@SerializerId2("org.radix.network2.transport.test.class")
	public static final class TestClass {
		// Placeholder for the serializer ID
		@JsonProperty(SerializerConstants.SERIALIZER_NAME)
		@DsonOutput(Output.ALL)
		private SerializerDummy serializer = SerializerDummy.DUMMY;

		@JsonProperty("metadata")
		@DsonOutput(Output.ALL)
		TransportMetadata metadata;

		TestClass() {
			// For serializer
		}

		public TestClass(TransportMetadata metadata) {
			this.metadata = metadata;
		}

		@Override
		public String toString() {
			return String.format("%s[%s]", getClass().getSimpleName(), metadata);
		}
	}

	@Test
	public void testDynamicMetadataSerializationToJson() throws SerializationException {
		Serialization serialization = Serialization.getDefault();

		TestClass tc = new TestClass(DynamicTransportMetadata.of("host", () -> "127.0.0.1", "port", () -> "30000"));

		// Serialize
		String serializedForm = serialization.toJson(tc, Output.ALL);

		// Deserialize
		TestClass deserializedTc = serialization.fromJson(serializedForm, TestClass.class);

		assertEquals("127.0.0.1", deserializedTc.metadata.get("host"));
		assertEquals("30000", deserializedTc.metadata.get("port"));
		assertEquals(StaticTransportMetadata.class, deserializedTc.metadata.getClass());
	}

	@Test
	public void testStaticMetadataSerializationToJson() throws SerializationException {
		Serialization serialization = Serialization.getDefault();

		TestClass tc = new TestClass(StaticTransportMetadata.of("host", "127.0.0.1", "port", "30000"));

		// Serialize
		String serializedForm = serialization.toJson(tc, Output.ALL);

		// Deserialize
		TestClass deserializedTc = serialization.fromJson(serializedForm, TestClass.class);

		assertEquals("127.0.0.1", deserializedTc.metadata.get("host"));
		assertEquals("30000", deserializedTc.metadata.get("port"));
		assertEquals(StaticTransportMetadata.class, deserializedTc.metadata.getClass());
	}

}
