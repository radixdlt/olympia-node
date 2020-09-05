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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerState;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerStateAndProof;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.ledger.StateComputerLedger.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.mempool.Mempool;
import java.util.Collections;
import java.util.function.BiConsumer;
import org.junit.Before;
import org.junit.Test;

public class StateComputerLedgerTest {

	private Mempool mempool;
	private StateComputer stateComputer;
	private StateComputerLedger stateComputerLedger;
	private CommittedStateSyncSender committedStateSyncSender;
	private CommittedSender committedSender;
	private VerifiedLedgerStateAndProof currentLedgerState;
	private SystemCounters counters;

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.stateComputer = mock(StateComputer.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.counters = mock(SystemCounters.class);
		this.committedSender = mock(CommittedSender.class);
		this.currentLedgerState = mock(VerifiedLedgerStateAndProof.class);

		this.stateComputerLedger = new StateComputerLedger(
			currentLedgerState,
			mempool,
			stateComputer,
			committedStateSyncSender,
			committedSender,
			counters
		);
	}

	@Test
	public void when_generate_proposal_with_empty_prepared__then_generate_proposal_should_return_atom() {
		Command command = mock(Command.class);
		when(mempool.getCommands(anyInt(), anySet())).thenReturn(Collections.singletonList(command));
		Command nextCommand = stateComputerLedger.generateNextCommand(View.of(1), Collections.emptySet());
		assertThat(command).isEqualTo(nextCommand);
	}

	@Test
	public void when_prepare_with_no_command_and_not_end_of_epoch__then_should_return_same_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);

		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(ledgerState.getStateVersion()).thenReturn(12345L);

		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerState()).thenReturn(ledgerState);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());

		LedgerState nextPrepared = stateComputerLedger.prepare(vertex);
		assertThat(nextPrepared.isEndOfEpoch()).isFalse();
		assertThat(nextPrepared.getStateVersion()).isEqualTo(12345L);
	}

	@Test
	public void when_prepare_and_parent_is_end_of_epoch__then_should_return_same_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);

		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(ledgerState.getStateVersion()).thenReturn(12345L);

		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerState()).thenReturn(ledgerState);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());

		LedgerState nextPrepared = stateComputerLedger.prepare(vertex);
		assertThat(nextPrepared.isEndOfEpoch()).isFalse();
		assertThat(nextPrepared.getStateVersion()).isEqualTo(12345L);
	}

	@Test
	public void when_prepare_with_no_command_and_end_of_epoch__then_should_return_same_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);

		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(ledgerState.getStateVersion()).thenReturn(12345L);

		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerState()).thenReturn(ledgerState);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(stateComputer.prepare(eq(vertex))).thenReturn(true);

		LedgerState nextPrepared = stateComputerLedger.prepare(vertex);
		assertThat(nextPrepared.isEndOfEpoch()).isTrue();
		assertThat(nextPrepared.getStateVersion()).isEqualTo(12345L);
	}

	@Test
	public void when_prepare_with_command_and_not_end_of_epoch__then_should_return_next_state_version() {
		Vertex vertex = mock(Vertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);

		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(ledgerState.getStateVersion()).thenReturn(12345L);

		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerState()).thenReturn(ledgerState);
		when(qc.getProposed()).thenReturn(parent);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getCommand()).thenReturn(mock(Command.class));

		LedgerState nextPrepared = stateComputerLedger.prepare(vertex);
		assertThat(nextPrepared.isEndOfEpoch()).isFalse();
		assertThat(nextPrepared.getStateVersion()).isEqualTo(12346L);
	}

	@Test
	public void when_commit_below_current_version__then_nothing_happens() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);

		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerStateAndProof proof = mock(VerifiedLedgerStateAndProof.class);
		when(proof.compareTo(eq(currentLedgerState))).thenReturn(-1);
		when(verified.getLedgerState()).thenReturn(proof);

		stateComputerLedger.commit(verified);
		verify(stateComputer, never()).commit(any());
		verify(mempool, never()).removeCommitted(any());
		verify(committedSender, never()).sendCommitted(any(), any());
	}

	@Test
	public void when_commit__then_correct_messages_are_sent() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);

		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerStateAndProof proof = mock(VerifiedLedgerStateAndProof.class);
		when(proof.compareTo(eq(currentLedgerState))).thenReturn(1);
		when(verified.getLedgerState()).thenReturn(proof);
		when(verified.truncateFromVersion(anyLong())).thenReturn(verified);
		doAnswer(invocation -> {
			BiConsumer<Long, Command> consumer = invocation.getArgument(0);
			consumer.accept(1L, command);
			return null;
		}).when(verified).forEach(any());

		stateComputerLedger.commit(verified);
		verify(stateComputer, times(1)).commit(argThat(v -> v.getLedgerState().equals(proof)));
		verify(mempool, times(1)).removeCommitted(eq(hash));
		verify(committedSender, times(1)).sendCommitted(any(), any());
	}

	@Test
	public void when_check_sync_and_synced__then_return_sync_handler() {
		VerifiedLedgerStateAndProof verifiedLedgerStateAndProof = mock(VerifiedLedgerStateAndProof.class);
		when(verifiedLedgerStateAndProof.compareTo(currentLedgerState)).thenReturn(0);

		Runnable onSynced = mock(Runnable.class);
		Runnable onNotSynced = mock(Runnable.class);
		stateComputerLedger
			.ifCommitSynced(verifiedLedgerStateAndProof)
			.then(onSynced)
			.elseExecuteAndSendMessageOnSync(onNotSynced, mock(Object.class));
		verify(onSynced, times(1)).run();
		verify(onNotSynced, never()).run();
	}

	@Test
	public void when_check_sync__will_complete_when_higher_or_equal_state_version() {
		when(currentLedgerState.getStateVersion()).thenReturn(0L);
		VerifiedLedgerStateAndProof verifiedLedgerStateAndProof = mock(VerifiedLedgerStateAndProof.class);
		when(verifiedLedgerStateAndProof.getStateVersion()).thenReturn(1L);

		Runnable onSynced = mock(Runnable.class);
		Runnable onNotSynced = mock(Runnable.class);
		stateComputerLedger
			.ifCommitSynced(verifiedLedgerStateAndProof)
			.then(onSynced)
			.elseExecuteAndSendMessageOnSync(onNotSynced, mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());
		verify(onSynced, never()).run();
		verify(onNotSynced, times(1)).run();

		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerStateAndProof proof = mock(VerifiedLedgerStateAndProof.class);
		when(proof.getStateVersion()).thenReturn(1L);
		when(proof.compareTo(any())).thenReturn(1);
		when(verified.getLedgerState()).thenReturn(proof);
		when(verified.truncateFromVersion(anyLong())).thenReturn(verified);

		stateComputerLedger.commit(verified);

		verify(committedStateSyncSender, timeout(5000).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}