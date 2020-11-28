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

package com.radixdlt.consensus.bft;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.AccumulatorState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreTest {
	private VerifiedVertex genesisVertex;
	private Supplier<VerifiedVertex> nextVertex;
	private Function<Boolean, VerifiedVertex> nextSkippableVertex;
	private HashCode genesisHash;
	private QuorumCertificate rootQC;
	private VertexStore sut;
	private Ledger ledger;
	private VertexStoreEventSender vertexStoreEventSender;
	private EventDispatcher<BFTUpdate> bftUpdateSender;
	private EventDispatcher<BFTCommittedUpdate> committedSender;
	private SystemCounters counters;
	private Hasher hasher = Sha256Hasher.withDefaultSerialization();

	private static final LedgerHeader MOCKED_HEADER = LedgerHeader.create(
		0, View.genesis(), new AccumulatorState(0, HashUtils.zero256()), 0
	);

	@Before
	public void setUp() {
		// No type check issues with mocking generic here
		Ledger ssc = mock(Ledger.class);
		this.ledger = ssc;
		// TODO: replace mock with the real thing
		doAnswer(invocation -> {
			VerifiedVertex verifiedVertex = invocation.getArgument(1);
			return Optional.of(new PreparedVertex(verifiedVertex, MOCKED_HEADER, ImmutableList.of(), ImmutableMap.of()));
		}).when(ledger).prepare(any(), any());

		this.vertexStoreEventSender = mock(VertexStoreEventSender.class);
		this.counters = new SystemCountersImpl();
		this.bftUpdateSender = rmock(EventDispatcher.class);
		this.committedSender = rmock(EventDispatcher.class);

		this.genesisHash = HashUtils.zero256();
		this.genesisVertex = new VerifiedVertex(UnverifiedVertex.createGenesis(MOCKED_HEADER), genesisHash);
		this.rootQC = QuorumCertificate.ofGenesis(genesisVertex, MOCKED_HEADER);
		this.sut = VertexStore.create(
			mock(PersistentVertexStore.class),
			VerifiedVertexStoreState.create(HighQC.from(rootQC), genesisVertex),
			ledger,
			bftUpdateSender,
			committedSender,
			vertexStoreEventSender,
			counters
		);

		AtomicReference<BFTHeader> lastParentHeader
			= new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, MOCKED_HEADER));
		AtomicReference<BFTHeader> lastGrandParentHeader
			= new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, MOCKED_HEADER));
		AtomicReference<BFTHeader> lastGreatGrandParentHeader
			= new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, MOCKED_HEADER));

		this.nextSkippableVertex = (skipOne) -> {
			BFTHeader parentHeader = lastParentHeader.get();
			BFTHeader grandParentHeader = lastGrandParentHeader.get();
			BFTHeader greatGrandParentHeader = lastGreatGrandParentHeader.get();
			final QuorumCertificate qc;
			if (!parentHeader.getView().equals(View.genesis())) {
				VoteData data = new VoteData(parentHeader, grandParentHeader, skipOne ? null : greatGrandParentHeader);
				qc = new QuorumCertificate(data, new TimestampedECDSASignatures());
			} else {
				qc = rootQC;
			}
			View view = parentHeader.getView().next();
			if (skipOne) {
				view = view.next();
			}

			UnverifiedVertex rawVertex = UnverifiedVertex.createVertex(qc, view, new Command(new byte[] {}));
			HashCode hash = hasher.hash(rawVertex);
			VerifiedVertex vertex = new VerifiedVertex(rawVertex, hash);
			lastParentHeader.set(new BFTHeader(view, hash, MOCKED_HEADER));
			lastGrandParentHeader.set(parentHeader);
			lastGreatGrandParentHeader.set(grandParentHeader);

			return vertex;
		};

		this.nextVertex = () -> nextSkippableVertex.apply(false);
	}

	@Test
	public void adding_a_qc_should_update_highest_qc() {
		// Arrange
		final List<VerifiedVertex> vertices = Stream.generate(this.nextVertex).limit(4).collect(Collectors.toList());
		sut.insertVertex(vertices.get(0));

		// Act
		QuorumCertificate qc = vertices.get(1).getQC();
		sut.addQC(qc);

		// Assert
		assertThat(sut.highQC().highestQC()).isEqualTo(qc);
		assertThat(sut.highQC().highestCommittedQC()).isEqualTo(rootQC);
	}

	@Test
	public void adding_a_qc_with_commit_should_commit_vertices_to_ledger() {
		// Arrange
		final List<VerifiedVertex> vertices = Stream.generate(this.nextVertex).limit(4).collect(Collectors.toList());
		sut.insertVertex(vertices.get(0));
		sut.insertVertex(vertices.get(1));
		sut.insertVertex(vertices.get(2));

		// Act
		QuorumCertificate qc = vertices.get(3).getQC();
		boolean success = sut.addQC(qc);

		// Assert
		assertThat(success).isTrue();
		assertThat(sut.highQC().highestQC()).isEqualTo(qc);
		assertThat(sut.highQC().highestCommittedQC()).isEqualTo(qc);
		assertThat(sut.getVertices(vertices.get(2).getId(), 3)).hasValue(ImmutableList.of(
			vertices.get(2), vertices.get(1), vertices.get(0)
		));
		verify(ledger, times(1)).commit(
			any(),
			argThat(l -> l.size() == 1 && l.get(0).getVertex().equals(vertices.get(0))),
			any()
		);
	}

	@Test
	public void adding_a_qc_which_has_not_been_inserted_should_return_false() {
		// Arrange
		this.nextVertex.get();

		// Act
		QuorumCertificate qc = this.nextVertex.get().getQC();
		boolean success = sut.addQC(qc);

		// Assert
		assertThat(success).isFalse();
	}

	@Test
	public void rebuilding_should_emit_updates() {
		// Arrange
		final List<VerifiedVertex> vertices = Stream.generate(this.nextVertex).limit(4).collect(Collectors.toList());
		VerifiedVertexStoreState vertexStoreState = VerifiedVertexStoreState.create(
			HighQC.from(vertices.get(3).getQC()),
			vertices.get(0),
			vertices.stream().skip(1).collect(ImmutableList.toImmutableList())
		);

		// Act
		sut.tryRebuild(vertexStoreState);

		// Assert
		verify(bftUpdateSender, times(1)).dispatch(argThat(u -> u.getInsertedVertex().equals(vertices.get(0))));
		verify(bftUpdateSender, times(1)).dispatch(argThat(u -> u.getInsertedVertex().equals(vertices.get(1))));
		verify(bftUpdateSender, times(1)).dispatch(argThat(u -> u.getInsertedVertex().equals(vertices.get(2))));
		verify(bftUpdateSender, times(1)).dispatch(argThat(u -> u.getInsertedVertex().equals(vertices.get(3))));
	}
}
