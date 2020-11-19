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

package com.radixdlt.statecomputer;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RadixEngineStakeComputerImpl}.
 */
public class RadixEngineStakeComputerImplTest {

	@Test
	public void when_created_with_null_token_rri__exception_is_thrown() {
		assertThatThrownBy(() -> RadixEngineStakeComputerImpl.create(null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void when_stake_added_for_staking_token_to_empty_computer__stake_is_present() {
		final var stakingToken = mock(RRI.class);
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;

		final var nextComputer = computer.addStake(delegatedKey, stakingToken, amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey)))
			.hasSize(1)
			.hasEntrySatisfying(delegatedKey, amount::equals);
	}

	@Test
	public void when_stakes_added_for_two_delegates_to_empty_computer__both_are_present() {
		final var stakingToken = mock(RRI.class);
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken);
		final var delegatedKey1 = ECKeyPair.generateNew().getPublicKey();
		final var delegatedKey2 = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;

		final var nextComputer = computer.addStake(delegatedKey1, stakingToken, amount)
			.addStake(delegatedKey1, stakingToken, amount)
			.addStake(delegatedKey2, stakingToken, amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey1, delegatedKey2)))
			.hasSize(2)
			.hasEntrySatisfying(delegatedKey1, amount::equals)
			.hasEntrySatisfying(delegatedKey2, amount::equals);
	}

	@Test
	public void when_stake_added_for_staking_token_to_non_empty_computer__stake_is_increased() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken)
			.addStake(delegatedKey, stakingToken, amount);

		final var nextComputer = computer.addStake(delegatedKey, stakingToken, amount);

		final var expectedAmount = amount.add(amount);
		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey)))
			.hasSize(1)
			.hasEntrySatisfying(delegatedKey, expectedAmount::equals);
	}

	@Test
	public void when_stake_added_for_non_staking_token_to_empty_computer__stake_is_unchanged() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken);

		final var nextComputer = computer.addStake(delegatedKey, mock(RRI.class), amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey)))
			.isEmpty();
	}

	@Test
	public void when_zero_stake_added_for_staking_token_to_empty_computer__stake_is_unchanged() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.ZERO;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken);

		final var nextComputer = computer.addStake(delegatedKey, stakingToken, amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey)))
			.isEmpty();
	}

	@Test
	public void when_stake_removed_from_computer__stake_is_empty() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken)
			.addStake(delegatedKey, stakingToken, amount);

		final var nextComputer = computer.removeStake(delegatedKey, stakingToken, amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey)))
			.isEmpty();
	}

	@Test
	public void when_stake_removed_from_computer_with_multiple_stakes__stake_is_not_empty() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey1 = ECKeyPair.generateNew().getPublicKey();
		final var delegatedKey2 = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken)
			.addStake(delegatedKey1, stakingToken, amount)
			.addStake(delegatedKey2, stakingToken, amount);

		final var nextComputer = computer.removeStake(delegatedKey1, stakingToken, amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey1, delegatedKey2)))
			.hasSize(1)
			.hasEntrySatisfying(delegatedKey2, amount::equals);
	}

	@Test
	public void when_part_stake_removed_from_computer_with_multiple_stakes__stake_is_not_empty() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey1 = ECKeyPair.generateNew().getPublicKey();
		final var delegatedKey2 = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken)
			.addStake(delegatedKey1, stakingToken, amount)
			.addStake(delegatedKey2, stakingToken, amount);

		final var nextComputer = computer.removeStake(delegatedKey1, stakingToken, UInt256.FIVE);

		final var expectedAmount = amount.subtract(UInt256.FIVE);
		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey1, delegatedKey2)))
			.hasSize(2)
			.hasEntrySatisfying(delegatedKey1, expectedAmount::equals)
			.hasEntrySatisfying(delegatedKey2, amount::equals);
	}

	@Test
	public void when_stake_removed_for_non_staking_token__stake_is_unchanged() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.TEN;
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken)
			.addStake(delegatedKey, stakingToken, amount);

		final var nextComputer = computer.removeStake(delegatedKey, mock(RRI.class), amount);

		assertThat(nextComputer.stakedAmounts(ImmutableSet.of(delegatedKey)))
			.hasSize(1)
			.hasEntrySatisfying(delegatedKey, amount::equals);
	}

	@Test
	public void sensibleToString() {
		final var stakingToken = mock(RRI.class);
		final var delegatedKey = ECKeyPair.generateNew().getPublicKey();
		final var amount = UInt256.from(1234567890L);
		final var computer = RadixEngineStakeComputerImpl.create(stakingToken)
			.addStake(delegatedKey, stakingToken, amount);

		final var string = computer.toString();

		assertThat(string)
			.contains(RadixEngineStakeComputerImpl.class.getSimpleName())
			.contains(stakingToken.toString())
			.contains(delegatedKey.toString())
			.contains(amount.toString());
	}
}
