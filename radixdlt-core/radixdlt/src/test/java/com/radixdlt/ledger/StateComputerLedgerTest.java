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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.TypedMocks;

import com.radixdlt.utils.UInt256;
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
	private EventDispatcher<LedgerUpdate> ledgerUpdateSender;
	private LedgerProof currentLedgerHeader;
	private SystemCounters counters;
	private Comparator<LedgerProof> headerComparator;
	private LedgerAccumulator accumulator;
	private LedgerAccumulatorVerifier accumulatorVerifier;

	private LedgerHeader ledgerHeader;
	private UnverifiedVertex genesis;
	private VerifiedVertex genesisVertex;
	private QuorumCertificate genesisQC;

	private final Command nextCommand = new Command(new byte[] {0});
	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();
	private final PreparedCommand successfulNextCommand = new PreparedCommand() {
		@Override
		public Command command() {
			return nextCommand;
		}

		@Override
		public HashCode hash() {
			return hasher.hash(nextCommand);
		}
	};

	private final long genesisEpoch = 3L;
	private final long genesisStateVersion = 123L;

	@Before
	public void setup() {
		this.mempool = TypedMocks.rmock(Mempool.class);
		// No type check issues with mocking generic here
		this.stateComputer = mock(StateComputer.class);
		this.counters = mock(SystemCounters.class);
		this.ledgerUpdateSender = TypedMocks.rmock(EventDispatcher.class);
		this.headerComparator = TypedMocks.rmock(Comparator.class);

		this.accumulator = new SimpleLedgerAccumulatorAndVerifier(hasher);
		this.accumulatorVerifier = new SimpleLedgerAccumulatorAndVerifier(hasher);

		this.ledgerHeader = LedgerHeader.genesis(HashUtils.zero256(), null);
		this.genesis = UnverifiedVertex.createGenesis(ledgerHeader);
		this.genesisVertex = new VerifiedVertex(genesis, hasher.hash(genesis));
		this.genesisQC = QuorumCertificate.ofGenesis(genesisVertex, ledgerHeader);
		this.currentLedgerHeader = this.genesisQC.getCommittedAndLedgerStateProof()
			.map(Pair::getSecond).orElseThrow();

		this.sut = new StateComputerLedger(
			mock(TimeSupplier.class),
			currentLedgerHeader,
			headerComparator,
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
			new AccumulatorState(genesisStateVersion, HashUtils.zero256()),
			12345,
			endOfEpoch ? BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE))) : null
		);
		this.genesis = UnverifiedVertex.createGenesis(ledgerHeader);
		this.genesisVertex = new VerifiedVertex(genesis, hasher.hash(genesis));
		this.genesisQC = QuorumCertificate.ofGenesis(genesisVertex, ledgerHeader);
		this.currentLedgerHeader = this.genesisQC.getCommittedAndLedgerStateProof()
			.map(Pair::getSecond).orElseThrow();

		this.sut = new StateComputerLedger(
			mock(TimeSupplier.class),
			currentLedgerHeader,
			headerComparator,
			stateComputer,
			ledgerUpdateSender,
			accumulator,
			accumulatorVerifier,
			counters
		);
	}

	@Test
	public void should_not_change_accumulator_when_there_is_no_command() {
		// Arrange
		genesisIsEndOfEpoch(false);
		when(stateComputer.prepare(any(), any(), anyLong(), any(), anyLong()))
			.thenReturn(new StateComputerResult(ImmutableList.of(), ImmutableMap.of()));
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
		when(stateComputer.prepare(any(), any(), anyLong(), any(), anyLong()))
			.thenReturn(new StateComputerResult(ImmutableList.of(successfulNextCommand), ImmutableMap.of()));
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
		when(stateComputer.prepare(any(), any(), anyLong(), any(), anyLong()))
			.thenReturn(new StateComputerResult(ImmutableList.of(successfulNextCommand), ImmutableMap.of()));

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
				hasher::hash,
				x.getLedgerHeader().getAccumulatorState()
			))
		).contains(ImmutableList.of(nextCommand));
	}

	@Test
	public void should_do_nothing_if_committing_lower_state_version() {
		// Arrange
		genesisIsEndOfEpoch(false);
		when(stateComputer.prepare(any(), any(), anyLong(), any(), anyLong()))
			.thenReturn(new StateComputerResult(ImmutableList.of(successfulNextCommand), ImmutableMap.of()));
		final AccumulatorState accumulatorState = new AccumulatorState(genesisStateVersion - 1, HashUtils.zero256());
		final LedgerHeader ledgerHeader = LedgerHeader.create(
			genesisEpoch,
			View.of(2),
			accumulatorState,
			1234
		);
		final LedgerProof header = new LedgerProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			12345,
			mock(HashCode.class),
			ledgerHeader,
			new TimestampedECDSASignatures()
		);
		VerifiedCommandsAndProof verified = new VerifiedCommandsAndProof(ImmutableList.of(nextCommand), header);

		// Act
		sut.syncEventProcessor().process(verified);

		// Assert
		verify(stateComputer, never()).commit(any(), any());
		verify(mempool, never()).committed(any());
		verify(ledgerUpdateSender, never()).dispatch(any());
	}
}
