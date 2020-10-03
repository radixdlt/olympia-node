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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.DsonSHA256Hasher;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.TypedMocks;

import com.radixdlt.utils.UInt256;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class StateComputerLedgerTest {

	private Mempool mempool;
	private StateComputer stateComputer;
	private StateComputerLedger sut;
	private LedgerUpdateSender ledgerUpdateSender;
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;
	private SystemCounters counters;
	private Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private LedgerAccumulator accumulator;
	private LedgerAccumulatorVerifier accumulatorVerifier;

	private LedgerHeader ledgerHeader;
	private UnverifiedVertex genesis;
	private VerifiedVertex genesisVertex;
	private QuorumCertificate genesisQC;

	private final Command nextCommand = new Command(new byte[] {0});

	private final long genesisEpoch = 3L;
	private final long genesisStateVersion = 123L;
	private final Hasher hasher = new DsonSHA256Hasher(DefaultSerialization.getInstance());

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.stateComputer = mock(StateComputer.class);
		this.counters = mock(SystemCounters.class);
		this.ledgerUpdateSender = mock(LedgerUpdateSender.class);
		this.headerComparator = TypedMocks.rmock(Comparator.class);

		this.accumulator = new SimpleLedgerAccumulatorAndVerifier(hasher);
		this.accumulatorVerifier = new SimpleLedgerAccumulatorAndVerifier(hasher);

		this.ledgerHeader = LedgerHeader.genesis(Hash.ZERO_HASH, null);
		this.genesis = UnverifiedVertex.createGenesis(ledgerHeader);
		this.genesisVertex = new VerifiedVertex(genesis, hasher.hash(genesis));
		this.genesisQC = QuorumCertificate.ofGenesis(genesisVertex, ledgerHeader);
		this.currentLedgerHeader = this.genesisQC.getCommittedAndLedgerStateProof()
			.map(Pair::getSecond).orElseThrow();

		this.sut = new StateComputerLedger(
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

	public void genesisIsEndOfEpoch(boolean endOfEpoch) {
		this.ledgerHeader = LedgerHeader.create(
			genesisEpoch,
			View.of(5),
			new AccumulatorState(genesisStateVersion, Hash.ZERO_HASH),
			12345,
			endOfEpoch ? BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE))) : null
		);
		this.genesis = UnverifiedVertex.createGenesis(ledgerHeader);
		this.genesisVertex = new VerifiedVertex(genesis, hasher.hash(genesis));
		this.genesisQC = QuorumCertificate.ofGenesis(genesisVertex, ledgerHeader);
		this.currentLedgerHeader = this.genesisQC.getCommittedAndLedgerStateProof()
			.map(Pair::getSecond).orElseThrow();

		this.sut = new StateComputerLedger(
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
		Command nextCommand = sut.generateNextCommand(View.of(1), Collections.emptySet());
		assertThat(command).isEqualTo(nextCommand);
	}

	@Test
	public void should_not_change_accumulator_when_there_is_no_command() {
		// Arrange
		genesisIsEndOfEpoch(false);
		when(stateComputer.prepare(any(), any())).thenReturn(new StateComputerResult());
		final UnverifiedVertex unverifiedVertex = new UnverifiedVertex(genesisQC, View.of(1), null);
		final VerifiedVertex proposedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));

		// Act
		Optional<PreparedVertex> nextPrepared = sut.prepare(new LinkedList<>(), proposedVertex);

		// Assert
		assertThat(nextPrepared)
			.hasValueSatisfying(x -> assertThat(x.getLedgerHeader().isEndOfEpoch()).isFalse());
		assertThat(nextPrepared)
			.hasValueSatisfying(x -> assertThat(x.getLedgerHeader().getAccumulatorState()).isEqualTo(ledgerHeader.getAccumulatorState()));
	}

	@Test
	public void should_not_change_header_when_past_end_of_epoch_even_with_command() {
		// Arrange
		genesisIsEndOfEpoch(true);
		when(stateComputer.prepare(any(), any())).thenReturn(new StateComputerResult());
		final UnverifiedVertex unverifiedVertex = new UnverifiedVertex(genesisQC, View.of(1), nextCommand);
		final VerifiedVertex proposedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));

		// Act
		Optional<PreparedVertex> nextPrepared = sut.prepare(new LinkedList<>(), proposedVertex);

		// Assert
		assertThat(nextPrepared)
			.hasValueSatisfying(x -> assertThat(x.getLedgerHeader().isEndOfEpoch()).isTrue());
		assertThat(nextPrepared)
			.hasValueSatisfying(x -> assertThat(x.getLedgerHeader().getAccumulatorState()).isEqualTo(ledgerHeader.getAccumulatorState()));
	}

	@Test
	public void should_accumulate_when_next_command_valid() {
		// Arrange
		genesisIsEndOfEpoch(false);
		when(stateComputer.prepare(any(), any())).thenReturn(new StateComputerResult());

		// Act
		final UnverifiedVertex unverifiedVertex = new UnverifiedVertex(genesisQC, View.of(1), nextCommand);
		final VerifiedVertex proposedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));
		Optional<PreparedVertex> nextPrepared = sut.prepare(new LinkedList<>(), proposedVertex);

		// Assert
		assertThat(nextPrepared).hasValueSatisfying(x -> assertThat(x.getLedgerHeader().isEndOfEpoch()).isFalse());
		assertThat(nextPrepared.flatMap(x ->
			accumulatorVerifier.verifyAndGetExtension(
				ledgerHeader.getAccumulatorState(),
				ImmutableList.of(nextCommand),
				x.getLedgerHeader().getAccumulatorState()
			))
		).contains(ImmutableList.of(nextCommand));
	}

	@Test
	public void should_do_nothing_if_committing_lower_state_version() {
		// Arrange
		genesisIsEndOfEpoch(false);
		when(stateComputer.prepare(any(), any())).thenReturn(new StateComputerResult());
		final AccumulatorState accumulatorState = new AccumulatorState(genesisStateVersion - 1, Hash.ZERO_HASH);
		final LedgerHeader ledgerHeader = LedgerHeader.create(
			genesisEpoch,
			View.of(2),
			accumulatorState,
			1234,
			null
		);
		final VerifiedLedgerHeaderAndProof header = new VerifiedLedgerHeaderAndProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			12345,
			mock(Hash.class),
			ledgerHeader,
			new TimestampedECDSASignatures()
		);
		VerifiedCommandsAndProof verified = new VerifiedCommandsAndProof(ImmutableList.of(nextCommand), header);

		// Act
		sut.commit(verified);

		// Assert
		verify(stateComputer, never()).commit(any());
		verify(mempool, never()).removeCommitted(any());
		verify(ledgerUpdateSender, never()).sendLedgerUpdate(any());
	}
}