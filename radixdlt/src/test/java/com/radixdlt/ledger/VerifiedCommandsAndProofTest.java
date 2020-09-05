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

package com.radixdlt.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.utils.TypedMocks;

import java.util.function.BiConsumer;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class VerifiedCommandsAndProofTest {
	private Command command;
	private VerifiedLedgerHeaderAndProof stateAndProof;
	private VerifiedCommandsAndProof singleCommandAndProof;
	private VerifiedCommandsAndProof emptyCommandsAndProof;
	private final long stateVersion = 232L;

	@Before
	public void setUp() {
		this.stateAndProof = mock(VerifiedLedgerHeaderAndProof.class);
		when(stateAndProof.getStateVersion()).thenReturn(stateVersion);

		this.emptyCommandsAndProof = new VerifiedCommandsAndProof(ImmutableList.of(), stateAndProof);

		this.command = mock(Command.class);
		this.singleCommandAndProof = new VerifiedCommandsAndProof(ImmutableList.of(command), stateAndProof);
	}

	@Test
	public void when_empty_commands__then_first_version_should_be_proof_version() {
		assertThat(emptyCommandsAndProof.getFirstVersion()).isEqualTo(stateVersion);
	}

	@Test
	public void when_single_command__then_first_version_should_be_proof_version() {
		assertThat(singleCommandAndProof.getFirstVersion()).isEqualTo(stateVersion);
	}

	@Test
	public void when_empty_command_for_each__then_should_consume_appropriately() {
		BiConsumer<Long, Command> consumer = TypedMocks.rmock(BiConsumer.class);
		emptyCommandsAndProof.forEach(consumer);
		verify(consumer, never()).accept(any(), any());
	}

	@Test
	public void when_single_command_for_each__then_should_consume_appropriately() {
		BiConsumer<Long, Command> consumer = TypedMocks.rmock(BiConsumer.class);
		singleCommandAndProof.forEach(consumer);
		verify(consumer, times(1)).accept(eq(stateVersion), eq(command));
	}

	@Test
	public void when_empty_command_truncate_from_bad_version__then_should_throw_exception() {
		assertThatThrownBy(() -> emptyCommandsAndProof.truncateFromVersion(stateVersion - 2))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_single_command_truncate_from_bad_version__then_should_throw_exception() {
		assertThatThrownBy(() -> singleCommandAndProof.truncateFromVersion(stateVersion - 2))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_empty_command_truncate_from_perfect_version__then_should_return_equivalent() {
		VerifiedCommandsAndProof truncated = emptyCommandsAndProof.truncateFromVersion(stateVersion - 1);
		assertThat(truncated).isEqualTo(emptyCommandsAndProof);
	}

	@Test
	public void when_single_command_truncate_from_perfect_version__then_should_return_equivalent() {
		VerifiedCommandsAndProof truncated = singleCommandAndProof.truncateFromVersion(stateVersion - 1);
		assertThat(truncated).isEqualTo(singleCommandAndProof);
	}

	@Test
	public void when_empty_command_truncate_from_version__then_should_truncate_correctly() {
		VerifiedCommandsAndProof truncated = emptyCommandsAndProof.truncateFromVersion(stateVersion);
		assertThat(truncated.size()).isEqualTo(0);
	}

	@Test
	public void when_single_command_truncate_from_version__then_should_truncate_correctly() {
		VerifiedCommandsAndProof truncated = singleCommandAndProof.truncateFromVersion(stateVersion);
		assertThat(truncated.size()).isEqualTo(0);
	}

	@Test
	public void testGetters() {
		assertThat(this.emptyCommandsAndProof.getHeader()).isEqualTo(stateAndProof);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(VerifiedCommandsAndProof.class)
			.verify();
	}

}