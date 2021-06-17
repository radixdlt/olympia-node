/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.universe;

import org.apache.logging.log4j.util.Strings;
import org.radix.utils.IOUtils;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.qualifier.Magic;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.utils.Bytes;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Module which manages universe configuration
 */
public final class UniverseModule extends AbstractModule {
	@Provides
	@Magic
	public int magic(Universe universe) {
		return universe.getMagic();
	}

	@Provides
	@Singleton
	public Universe universe(RuntimeProperties properties, Serialization serialization) throws IOException {
		var universeString = properties.get("universe");
		return Strings.isNotBlank(universeString)
			? loadFromString(universeString, serialization)
			: loadFromFile(properties, serialization);
	}

	private Universe loadFromString(String universeString, Serialization serialization) throws DeserializeException {
		var bytes = Bytes.fromBase64String(universeString);
		return serialization.fromDson(bytes, Universe.class);
	}

	private Universe loadFromFile(RuntimeProperties properties, Serialization serialization) throws IOException {
		var universeFileName = properties.get("universe.location", ".//universe.json");
		try (var universeInput = new FileInputStream(universeFileName)) {
			return serialization.fromJson(IOUtils.toString(universeInput), Universe.class);
		}
	}

	@Provides
	@Genesis
	public VerifiedTxnsAndProof genesis(Universe universe) {
		return universe.getGenesis();
	}
}
