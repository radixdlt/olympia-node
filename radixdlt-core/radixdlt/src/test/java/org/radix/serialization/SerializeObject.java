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

import com.radixdlt.serialization.ClassScanningSerializerIds;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Polymorphic;
import com.radixdlt.serialization.Serialization;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.BeforeClass;
import org.junit.Test;

import com.radixdlt.serialization.DsonOutput.Output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	public static void serializeObjectBeforeClass() {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Configurator.setLevel(LogManager.getLogger(ClassScanningSerializerIds.class).getName(), Level.INFO);
	}

	private final Class<T> cls;
	private final Supplier<T> factory;

	protected SerializeObject(Class<T> cls, Supplier<T> factory) {
		this.cls = cls;
		this.factory = factory;
	}

	@Test
	public void testObjectHasEquals() throws NoSuchMethodException {
		Method method = factory.get().getClass().getMethod("equals", Object.class);
		assertFalse(method.getDeclaringClass().equals(Object.class));
	}

	@Test
	public void testObjectHasHashCode() throws NoSuchMethodException {
		Method method = factory.get().getClass().getMethod("hashCode");
		assertFalse(method.getDeclaringClass().equals(Object.class));
	}

	@Test
	public void testNONEIsEmpty() {
		String s2Json = getSerialization().toJson(factory.get(), Output.NONE);
		assertEquals("{}", s2Json);
	}

	@Test
	public void testRoundTripJsonSame() throws DeserializeException {
		checkPolymorphic();
		Serialization s = getSerialization();
		T initialObj = factory.get();
		String initialJson = s.toJson(initialObj, Output.ALL);
		T deserialisedObj = s.fromJson(initialJson, this.cls);
		assertEquals(initialObj, deserialisedObj);
	}

	@Test
	public void testRoundTripDsonSame() throws DeserializeException {
		checkPolymorphic();
		Serialization s = getSerialization();
		T initialObj = factory.get();
		byte[] initialDson = s.toDson(initialObj, Output.ALL);
		T deserialisedObj = s.fromDson(initialDson, this.cls);
		assertEquals(initialObj, deserialisedObj);
	}

	@Test
	public void testEncodeDecodeALL() throws Exception {
		checkPolymorphic();
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.ALL);
	}

	@Test
	public void testEncodeDecodeAPI() throws Exception {
		checkPolymorphic();
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.API);
	}

	// Output.HASH does not serialize "serializers" and can't be deserialized
	// Output.NONE does not serialize "serializers" and can't be deserialized

	@Test
	public void testEncodeDecodePERSIST() throws Exception {
		checkPolymorphic();
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.PERSIST);
	}

	@Test
	public void testEncodeDecodeWIRE() throws Exception {
		checkPolymorphic();
		testEncodeDecode(factory.get(), cls, getSerialization(), Output.WIRE);
	}

	private void checkPolymorphic() {
		assumeFalse("Not applicable for polymorphic classes", Polymorphic.class.isAssignableFrom(cls));
	}
}
