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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hash;
import com.radixdlt.consensus.LedgerHeader;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class VertexStoreTest {
	private VerifiedVertex genesisVertex;
	private Function<Hash, VerifiedVertex> nextVertex;
	private BiFunction<Hash, Boolean, VerifiedVertex> nextSkippableVertex;
	private Hash genesisHash;
	private QuorumCertificate rootQC;
	private VertexStore vertexStore;
	private Ledger ledger;
	private VertexStoreEventSender vertexStoreEventSender;
	private BFTUpdateSender bftUpdateSender;
	private SystemCounters counters;

	@Before
	public void setUp() {
		// No type check issues with mocking generic here
		Ledger ssc = mock(Ledger.class);
		this.ledger = ssc;
		when(this.ledger.prepare(any(), any())).thenReturn(Optional.of(mock(PreparedVertex.class)));
		this.vertexStoreEventSender = mock(VertexStoreEventSender.class);
		this.counters = mock(SystemCounters.class);
		this.bftUpdateSender = mock(BFTUpdateSender.class);

		this.genesisHash = mock(Hash.class);
		this.genesisVertex = new VerifiedVertex(UnverifiedVertex.createGenesis(mock(LedgerHeader.class)), genesisHash);
		this.rootQC = QuorumCertificate.ofGenesis(genesisVertex, mock(LedgerHeader.class));
		this.vertexStore = new VertexStore(
			genesisVertex,
			rootQC,
			ledger,
			bftUpdateSender,
			vertexStoreEventSender,
			counters
		);

		AtomicReference<BFTHeader> lastParentHeader
			= new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, mock(LedgerHeader.class)));
		AtomicReference<BFTHeader> lastGrandParentHeader
			= new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, mock(LedgerHeader.class)));
		AtomicReference<BFTHeader> lastGreatGrandParentHeader
			= new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, mock(LedgerHeader.class)));

		this.nextSkippableVertex = (hash, skipOne) -> {
			BFTHeader parentHeader = lastParentHeader.get();
			BFTHeader grandParentHeader = lastGrandParentHeader.get();
			BFTHeader greatGrandParentHeader = lastGreatGrandParentHeader.get();
			UnverifiedVertex rawVertex = mock(UnverifiedVertex.class);
			VerifiedVertex vertex = new VerifiedVertex(rawVertex, hash);
			final QuorumCertificate qc;
			if (!parentHeader.getView().equals(View.genesis())) {
				VoteData data = new VoteData(parentHeader, grandParentHeader, skipOne ? null : greatGrandParentHeader);
				qc = new QuorumCertificate(data, new TimestampedECDSASignatures());
			} else {
				qc = rootQC;
			}
			when(rawVertex.getQC()).thenReturn(qc);

			final View view;
			if (skipOne) {
				view = parentHeader.getView().next().next();
			} else {
				view = parentHeader.getView().next();
			}
			when(rawVertex.getView()).thenReturn(view);

			lastParentHeader.set(new BFTHeader(view, hash, mock(LedgerHeader.class)));
			lastGrandParentHeader.set(parentHeader);
			lastGreatGrandParentHeader.set(grandParentHeader);

			return vertex;
		};

		this.nextVertex = hash -> nextSkippableVertex.apply(hash, false);
	}

	@Test
	public void when_vertex_store_created_with_incorrect_roots__then_exception_is_thrown() {
		BFTHeader nextHeader = new BFTHeader(View.of(1), mock(Hash.class), mock(LedgerHeader.class));
		BFTHeader genesisHeader = new BFTHeader(View.of(0), genesisHash, mock(LedgerHeader.class));
		VoteData voteData = new VoteData(nextHeader, genesisHeader, null);
		QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		assertThatThrownBy(() ->
			new VertexStore(
				genesisVertex,
				badRootQC,
				ledger,
				bftUpdateSender,
				vertexStoreEventSender,
				counters
			)
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_vertex_store_created_with_correct_vertices__then_exception_is_not_thrown() {
		VerifiedVertex nextVertex = this.nextVertex.apply(mock(Hash.class));
		this.vertexStore = new VertexStore(
			genesisVertex,
			rootQC,
			Collections.singletonList(nextVertex),
			ledger,
			bftUpdateSender,
			vertexStoreEventSender,
			counters
		);
	}

	@Test
	public void when_vertex_store_created_with_incorrect_vertices__then_exception_is_thrown() {
		this.nextVertex.apply(mock(Hash.class));

		assertThatThrownBy(() ->
			new VertexStore(
				genesisVertex,
				rootQC,
				Collections.singletonList(this.nextVertex.apply(mock(Hash.class))),
				ledger,
				bftUpdateSender,
				vertexStoreEventSender,
				counters
			)
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@Ignore("Need to catch this at verification of object")
	public void when_inserting_vertex_with_missing_parent__then_missing_parent_exception_is_thrown() {
		/*
		VertexMetadata vertexMetadata = VertexMetadata.ofGenesisAncestor();
		VoteData voteData = new VoteData(vertexMetadata, null, null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		Vertex nextVertex = Vertex.createVertex(qc, View.of(1), mock(ClientAtom.class));
		assertThatThrownBy(() -> vertexStore.insertVertex(nextVertex))
			.isInstanceOf(MissingParentException.class);
		 */
	}

	@Test
	public void when_add_qc_which_was_not_inserted__then_false_is_returned() {
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(2));
		when(header.getVertexId()).thenReturn(mock(Hash.class));

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		assertThat(vertexStore.addQC(qc)).isFalse();
	}
}