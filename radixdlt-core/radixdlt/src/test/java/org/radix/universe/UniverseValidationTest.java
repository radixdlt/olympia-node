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

package org.radix.universe;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Sha256Hasher;
import java.io.IOException;
import java.util.Properties;

import org.junit.Test;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

/**
 * Checks that "universe" property in resource
 * default.config can be deserialised and validated.
 */
public class UniverseValidationTest {
	private static final String PROPERTIES_FILE = "/default.config";

	@Test
	public void testLoadingUniverse() throws Exception {
		System.out.println(universeBase64());
		byte[] bytes = Bytes.fromBase64String(universeBase64());
		Universe universe = DefaultSerialization.getInstance().fromDson(bytes, Universe.class);
		UniverseValidator.validate(universe, Sha256Hasher.withDefaultSerialization());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testLoadingUniverseHasImmutableGenesis() throws Exception {
		byte[] bytes = Bytes.fromBase64String(universeBase64());
		Universe universe = DefaultSerialization.getInstance().fromDson(bytes, Universe.class);
		universe.getGenesis().add(new Atom());
	}

	private String universeBase64() throws IOException {
		final var properties = new Properties();
		try (final var input = this.getClass().getResourceAsStream(PROPERTIES_FILE)) {
			if (input == null) {
				throw new IOException("Resource not found: " + PROPERTIES_FILE);
			}
			properties.load(input);
		}
		return (String) properties.get("universe");
	}
}
