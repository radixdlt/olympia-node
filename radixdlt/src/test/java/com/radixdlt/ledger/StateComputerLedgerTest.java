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
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.TypedMocks;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class StateComputerLedgerTest {

	private Mempool mempool;
	private StateComputer stateComputer;
	private StateComputerLedger stateComputerLedger;
	private LedgerUpdateSender ledgerUpdateSender;
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;
	private SystemCounters counters;
	private Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private LedgerAccumulator accumulator;
	private LedgerAccumulatorVerifier accumulatorVerifier;


	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.stateComputer = mock(StateComputer.class);
		this.counters = mock(SystemCounters.class);
		this.ledgerUpdateSender = mock(LedgerUpdateSender.class);
		this.currentLedgerHeader = mock(VerifiedLedgerHeaderAndProof.class);
		this.headerComparator = TypedMocks.rmock(Comparator.class);
		this.accumulator = mock(LedgerAccumulator.class);
		this.accumulatorVerifier = mock(LedgerAccumulatorVerifier.class);
		when(accumulator.accumulate(any(), any(Command.class))).thenReturn(mock(AccumulatorState.class));

		this.stateComputerLedger = new StateComputerLedger(
			headerComparator,
			currentLedgerHeader,
			mempool,
			stateComputer,
			ledgerUpdateSender,
			accumulator,
			accumulatorVerifier,
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
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getQC()).thenReturn(qc);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.isEndOfEpoch()).thenReturn(false);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(12345L);
		when(accumulatorState.getAccumulatorHash()).thenReturn(mock(Hash.class));
		when(ledgerHeader.getAccumulatorState()).thenReturn(accumulatorState);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerHeader()).thenReturn(ledgerHeader);
		when(vertex.getParentHeader()).thenReturn(parent);
		when(accumulatorVerifier.verifyAndGetExtension(any(), any(), any())).thenReturn(Optional.of(ImmutableList.of()));

		Optional<LedgerHeader> nextPrepared = stateComputerLedger.prepare(new LinkedList<>(), vertex);

		assertThat(nextPrepared).isNotEmpty();
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.isEndOfEpoch()).isFalse());
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.getAccumulatorState().getStateVersion()).isEqualTo(12345L));
	}

	@Test
	public void when_prepare_and_parent_is_end_of_epoch__then_should_return_same_state_version() {
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getQC()).thenReturn(qc);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.isEndOfEpoch()).thenReturn(false);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(12345L);
		when(accumulatorState.getAccumulatorHash()).thenReturn(mock(Hash.class));
		when(ledgerHeader.getAccumulatorState()).thenReturn(accumulatorState);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerHeader()).thenReturn(ledgerHeader);
		when(vertex.getParentHeader()).thenReturn(parent);
		when(accumulatorVerifier.verifyAndGetExtension(any(), any(), any())).thenReturn(Optional.of(ImmutableList.of()));

		Optional<LedgerHeader> nextPrepared = stateComputerLedger.prepare(new LinkedList<>(), vertex);

		assertThat(nextPrepared).isNotEmpty();
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.isEndOfEpoch()).isFalse());
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.getAccumulatorState().getStateVersion()).isEqualTo(12345L));
	}

	@Test
	public void when_prepare_with_no_command_and_end_of_epoch__then_should_return_same_state_version() {
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		View view = mock(View.class);
		when(vertex.getView()).thenReturn(view);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getQC()).thenReturn(qc);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.isEndOfEpoch()).thenReturn(false);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(12345L);
		when(accumulatorState.getAccumulatorHash()).thenReturn(mock(Hash.class));
		when(ledgerHeader.getAccumulatorState()).thenReturn(accumulatorState);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerHeader()).thenReturn(ledgerHeader);
		when(vertex.getParentHeader()).thenReturn(parent);

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(mock(BFTValidator.class)));
		StateComputerResult result = mock(StateComputerResult.class);
		when(result.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));
		when(stateComputer.prepare(eq(ImmutableList.of()), eq(view))).thenReturn(result);
		when(accumulatorVerifier.verifyAndGetExtension(any(), any(), any())).thenReturn(Optional.of(ImmutableList.of()));

		Optional<LedgerHeader> nextPrepared = stateComputerLedger.prepare(new LinkedList<>(), vertex);

		assertThat(nextPrepared).isNotEmpty();
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.isEndOfEpoch()).isTrue());
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.getAccumulatorState().getStateVersion()).isEqualTo(12345L));
	}

	@Test
	public void when_prepare_with_command_and_not_end_of_epoch__then_should_accumulate() {
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getTimestampedSignatures()).thenReturn(new TimestampedECDSASignatures());
		when(vertex.getQC()).thenReturn(qc);
		LedgerHeader parentHeader = mock(LedgerHeader.class);
		when(parentHeader.isEndOfEpoch()).thenReturn(false);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(12345L);
		when(accumulatorState.getAccumulatorHash()).thenReturn(mock(Hash.class));
		when(parentHeader.getAccumulatorState()).thenReturn(accumulatorState);
		AccumulatorState nextAccumulateState = mock(AccumulatorState.class);
		when(nextAccumulateState.getStateVersion()).thenReturn(12346L);
		when(nextAccumulateState.getAccumulatorHash()).thenReturn(mock(Hash.class));
		Command command = mock(Command.class);
		when(accumulator.accumulate(eq(accumulatorState), eq(command))).thenReturn(nextAccumulateState);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getLedgerHeader()).thenReturn(parentHeader);
		when(vertex.getParentHeader()).thenReturn(parent);
		when(vertex.getCommand()).thenReturn(command);
		when(accumulatorVerifier.verifyAndGetExtension(any(), any(), any())).thenReturn(Optional.of(ImmutableList.of()));

		Optional<LedgerHeader> nextPrepared = stateComputerLedger.prepare(new LinkedList<>(), vertex);

		assertThat(nextPrepared).isNotEmpty();
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.isEndOfEpoch()).isFalse());
		assertThat(nextPrepared)
			.hasValueSatisfying(l -> assertThat(l.getAccumulatorState().getStateVersion()).isEqualTo(12346L));
	}

	@Test
	public void when_commit_below_current_version__then_nothing_happens() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);

		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(headerComparator.compare(eq(proof), eq(currentLedgerHeader))).thenReturn(-1);
		when(verified.getHeader()).thenReturn(proof);

		stateComputerLedger.commit(verified);
		verify(stateComputer, never()).commit(any());
		verify(mempool, never()).removeCommitted(any());
		verify(ledgerUpdateSender, never()).sendLedgerUpdate(any());
	}

	@Test
	public void when_commit__then_correct_messages_are_sent() {
		Command command = mock(Command.class);
		Hash hash = mock(Hash.class);
		when(command.getHash()).thenReturn(hash);

		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(headerComparator.compare(eq(proof), eq(currentLedgerHeader))).thenReturn(1);
		when(verified.getHeader()).thenReturn(proof);
		when(proof.getAccumulatorState()).thenReturn(mock(AccumulatorState.class));
		when(accumulatorVerifier.verifyAndGetExtension(any(), any(), any())).thenReturn(Optional.of(ImmutableList.of(command)));

		stateComputerLedger.commit(verified);
		verify(stateComputer, times(1)).commit(argThat(v -> v.getHeader().equals(proof)));
		verify(mempool, times(1)).removeCommitted(eq(hash));
		verify(ledgerUpdateSender, times(1)).sendLedgerUpdate(any());
	}
}