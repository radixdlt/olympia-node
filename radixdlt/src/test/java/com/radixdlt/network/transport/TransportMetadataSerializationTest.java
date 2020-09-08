/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.transport;

import com.radixdlt.DefaultSerialization;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
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
	public void testDynamicMetadataSerializationToJson() throws DeserializeException {
		Serialization serialization = DefaultSerialization.getInstance();

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
	public void testStaticMetadataSerializationToJson() throws DeserializeException {
		Serialization serialization = DefaultSerialization.getInstance();

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
