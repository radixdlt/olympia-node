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

package com.radixdlt.middleware2;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class LedgerAtomSerializationTest {
	private final Serialization serialization = Serialization.create(
			ClasspathScanningSerializerIds.create(),
			ClasspathScanningSerializationPolicy.create()
	);

	@Test
	public void oneSubclassCanBeSerializedAndDeserializedVia() throws Exception {
		final ClientAtom clientAtom = ClientAtom.create("metadata");

		var json = serialization.toJson(clientAtom, Output.ALL);
		var obj = serialization.fromJson(json, LedgerAtom.class);

		assertThat(clientAtom).isEqualTo(obj);
	}

	@Test
	public void siblingClassCanBeSerializedAndDeserializedViaInterface() throws Exception {
		final DifferentClientAtom clientAtom = DifferentClientAtom.create("datameta");

		var json = serialization.toJson(clientAtom, Output.ALL);
		var obj = serialization.fromJson(json, LedgerAtom.class);

		assertThat(clientAtom).isEqualTo(obj);
	}

	@Test
	public void deeperInheritedClassCanBeSerializedAndDeserializedViaInterface() throws Exception {
		final ExtendedClientAtom clientAtom = ExtendedClientAtom.create("meta", "extra");

		var json = serialization.toJson(clientAtom, Output.ALL);
		var obj = serialization.fromJson(json, LedgerAtom.class);

		assertThat(clientAtom).isEqualTo(obj);
	}
}