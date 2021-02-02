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
import com.radixdlt.properties.RuntimeProperties;

import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Test;
import org.radix.Radix;
import org.radix.utils.IOUtils;

import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

public class UniverseValidationTest {

	@Test
	public void testLoadingUniverse() throws Exception {
		byte[] bytes = Bytes.fromBase64String(loadProperties().get("universe"));
		Universe universe = DefaultSerialization.getInstance().fromDson(bytes, Universe.class);
		UniverseValidator.validate(universe, Sha256Hasher.withDefaultSerialization());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testLoadingUniverseHasImmutableGenesis() throws Exception {
		byte[] bytes = Bytes.fromBase64String(loadProperties().get("universe"));
		Universe universe = DefaultSerialization.getInstance().fromDson(bytes, Universe.class);
		universe.getGenesis().add(new Atom());
	}

	private RuntimeProperties loadProperties() throws ParseException, IOException {
		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null) {
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));
		}
		return new RuntimeProperties(runtimeConfigurationJSON, null);
	}
}
