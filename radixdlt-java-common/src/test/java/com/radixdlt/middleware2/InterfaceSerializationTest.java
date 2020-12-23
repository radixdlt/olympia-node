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

import static org.assertj.core.api.Assertions.assertThat;

public class InterfaceSerializationTest {
	private final Serialization serialization = Serialization.create(
			ClasspathScanningSerializerIds.create(),
			ClasspathScanningSerializationPolicy.create()
	);

	@Test
	public void one_subclass_can_be_serialized_and_deserialized_via_interface() throws Exception {
		final var clientAtom = TestClientAtom.create("metadata");

		final var json = serialization.toJson(clientAtom, Output.ALL);
		final var obj = serialization.fromJson(json, TestLedgerAtom.class);

		assertThat(clientAtom).isEqualTo(obj);
	}

	@Test
	public void sibling_class_can_be_serialized_and_deserialized_via_interface() throws Exception {
		final var clientAtom = TestDifferentClientAtom.create("datameta");

		final var json = serialization.toJson(clientAtom, Output.ALL);
		final var obj = serialization.fromJson(json, TestLedgerAtom.class);

		assertThat(clientAtom).isEqualTo(obj);
	}

	@Test
	public void deeper_inherited_class_can_be_serialized_and_deserialized_via_interface() throws Exception {
		final var clientAtom = TestExtendedClientAtom.create("meta", "extra");

		final var json = serialization.toJson(clientAtom, Output.ALL);
		final var obj = serialization.fromJson(json, TestLedgerAtom.class);

		assertThat(clientAtom).isEqualTo(obj);
	}

	@Test
	public void embedded_interface_can_be_serialized_and_deserialized() throws Exception {
		final var clientAtom = TestExtendedClientAtom.create("meta", "extra");
		final var container = TestEmbeddedInterfaceAtom.create(clientAtom);

		final var json = serialization.toJson(container, Output.ALL);
		final var obj = serialization.fromJson(json, TestEmbeddedInterfaceAtom.class);

		assertThat(container).isEqualTo(obj);
		assertThat(clientAtom).isEqualTo(obj.ledgerAtom());
	}
}