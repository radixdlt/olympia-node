/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.statecomputer;

import static org.assertj.core.api.Assertions.assertThat;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;

public class RadixEngineValidatorsComputerImplTest {
	@Test
	public void when_add_new__then_should_return_full_set() {
		final var key1 = ECKeyPair.generateNew().getPublicKey();
		final var key2 = ECKeyPair.generateNew().getPublicKey();
		final var computer = RadixEngineValidatorsComputerImpl.create()
			.addValidator(new RadixAddress((byte) 0, key1));

		final var nextComputer = computer.addValidator(new RadixAddress((byte) 0, key2));

		assertThat(nextComputer.activeValidators())
			.hasSize(2)
			.contains(key1, key2);
	}

	@Test
	public void when_add_existing__then_should_return_full_set() {
		final var key1 = ECKeyPair.generateNew().getPublicKey();
		final var key2 = ECKeyPair.generateNew().getPublicKey();
		final var computer = RadixEngineValidatorsComputerImpl.create()
			.addValidator(new RadixAddress((byte) 0, key1))
			.addValidator(new RadixAddress((byte) 0, key2));

		final var nextComputer = computer.addValidator(new RadixAddress((byte) 0, key2));

		assertThat(nextComputer.activeValidators())
			.hasSize(2)
			.contains(key1, key2);
	}

	@Test
	public void when_remove_existing__then_should_return_reduced_set() {
		final var key1 = ECKeyPair.generateNew().getPublicKey();
		final var key2 = ECKeyPair.generateNew().getPublicKey();
		final var computer = RadixEngineValidatorsComputerImpl.create()
			.addValidator(new RadixAddress((byte) 0, key1))
			.addValidator(new RadixAddress((byte) 0, key2));

		final var nextComputer = computer.removeValidator(new RadixAddress((byte) 0, key2));

		assertThat(nextComputer.activeValidators())
			.hasSize(1)
			.contains(key1);
	}

	@Test
	public void when_remove_non_existing__then_should_return_full_set() {
		final var key1 = ECKeyPair.generateNew().getPublicKey();
		final var key2 = ECKeyPair.generateNew().getPublicKey();
		final var computer = RadixEngineValidatorsComputerImpl.create()
			.addValidator(new RadixAddress((byte) 0, key1))
			.addValidator(new RadixAddress((byte) 0, key2));

		final var nextComputer = computer.removeValidator(new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey()));

		assertThat(nextComputer.activeValidators())
			.hasSize(2)
			.contains(key1, key2);
	}
}