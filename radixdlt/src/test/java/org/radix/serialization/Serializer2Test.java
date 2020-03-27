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

package org.radix.serialization;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import java.nio.charset.StandardCharsets;

import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.logging.Logging;
import com.radixdlt.serialization.DsonOutput.Output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test that round-trips some objects through the serializer.
 * Additional tests for the "serializer" field added.
 */
public class Serializer2Test extends RadixTest {

	private static Serialization serialization;

	private static DummyTestObject testObject;

	private static String jacksonJson;
	private static byte[] jacksonDson;

	@BeforeClass
	public static void beforeClass() throws Exception {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Logging.getLogger().setLevels(Logging.ALL & ~Logging.INFO & ~Logging.TRACE & ~Logging.DEBUG);
		TestSetupUtils.installBouncyCastleProvider();

		serialization = DefaultSerialization.getInstance();

		testObject = new DummyTestObject(true);

		jacksonJson = serialization.toJson(testObject, Output.ALL);
		jacksonDson = serialization.toDson(testObject, Output.ALL);

		DummyTestObject jacksonJsonObj = serialization.fromJson(jacksonJson, DummyTestObject.class);
		DummyTestObject jacksonCborObj = serialization.fromDson(jacksonDson, DummyTestObject.class);

		assertTrue(testObject.equals(jacksonJsonObj));
		assertTrue(testObject.equals(jacksonCborObj));
	}

	@Test
	public void roundTripJacksonDsonTest() throws SerializationException {
		byte[] bytes = serialization.toDson(testObject, Output.ALL);
		DummyTestObject newObject = serialization.fromDson(bytes, DummyTestObject.class);
		assertEquals(testObject, newObject);
	}

	@Test
	public void roundTripJacksonJsonTest() throws SerializationException {
		String json = serialization.toJson(testObject, Output.ALL);
		DummyTestObject newObject = serialization.fromJson(json, DummyTestObject.class);
		assertEquals(testObject, newObject);
	}

	@Test
	public void checkJsonSerializerInclusion() throws SerializationException {
		String json = serialization.toJson(testObject, Output.HASH);
		assertTrue(json.contains("serializer"));
		json = serialization.toJson(testObject, Output.WIRE);
		assertTrue(json.contains("serializer"));
	}

	@Test
	public void checkDsonSerializerInclusion() throws SerializationException {
		byte[] dson = serialization.toDson(testObject, Output.HASH);
		assertTrue(contains(dson, "serializer".getBytes(StandardCharsets.UTF_8)));
		dson = serialization.toDson(testObject, Output.WIRE);
		assertTrue(contains(dson, "serializer".getBytes(StandardCharsets.UTF_8)));
	}

	private static boolean contains(byte[] haystack, byte[] needle) {
		if (needle.length > haystack.length) {
			return false;
		}
		int length = needle.length;
		int imax = haystack.length - length;
		for (int i = 0; i <= imax; ++i) {
			if (equals(haystack, i, needle, 0, length)) {
				return true;
			}
		}
		return false;
	}

	private static boolean equals(byte[] a1, int offset1, byte[] a2, int offset2, int length) {
		for (int i = 0; i < length; ++i) {
			if (a1[offset1 + i] != a2[offset2 + i]) {
				return false;
			}
		}
		return true;
	}
}
