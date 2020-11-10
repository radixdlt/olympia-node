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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;

import java.util.function.Predicate;

import org.junit.Test;

public class RadixEngineValidatorSetBuilderTest {
	@Test
	public void testBadConstruction() {
		ImmutableSet<ECPublicKey> testSet = ImmutableSet.of();
		Predicate<ImmutableSet<ECPublicKey>> testPredicate = set -> false;
		assertThatThrownBy(() -> new RadixEngineValidatorSetBuilder(testSet, testPredicate))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_add_and_check_succeeds__then_should_return_full_set() {
		ECPublicKey key = ECKeyPair.generateNew().getPublicKey();
		RadixEngineValidatorSetBuilder builder = new RadixEngineValidatorSetBuilder(
			ImmutableSet.of(key), set -> !set.isEmpty()
		);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		RadixEngineValidatorSetBuilder nextBuilder = builder.addValidator(address);
		assertThat(nextBuilder.build().getValidators()).hasSize(2);
	}

	@Test
	public void when_check_fails__then_should_return_last_good() {
		ECPublicKey key = ECKeyPair.generateNew().getPublicKey();
		RadixEngineValidatorSetBuilder builder = new RadixEngineValidatorSetBuilder(
			ImmutableSet.of(key), set -> !set.isEmpty()
		);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(key);
		builder.removeValidator(address);
		assertThat(builder.build().getValidators()).hasSize(1);
		assertThat(builder.build().getValidators())
			.hasOnlyOneElementSatisfying(v -> assertThat(v.getNode().getKey()).isEqualTo(key));
	}
}