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

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.ClassScanningSerializerIds;
import com.radixdlt.serialization.Polymorphic;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.function.Supplier;

import static org.junit.Assume.assumeFalse;
import static org.radix.serialization.SerializationTestUtils.testEncodeDecode;

public abstract class SerializeValue<T> extends RadixTest {
	@BeforeClass
	public static void serializeObjectBeforeClass() {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Configurator.setLevel(LogManager.getLogger(ClassScanningSerializerIds.class).getName(), Level.INFO);
	}

	private final Class<T> cls;
	private final Supplier<T> factory;

	protected SerializeValue(Class<T> cls, Supplier<T> factory) {
		this.cls = cls;
		this.factory = factory;
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
