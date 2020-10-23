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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreCreationTest {
	private VerifiedVertex genesisVertex;
	private HashCode genesisHash;
	private Ledger ledger;
	private VertexStoreEventSender vertexStoreEventSender;
	private BFTUpdateSender bftUpdateSender;
	private SystemCounters counters;
	private static final LedgerHeader MOCKED_HEADER = LedgerHeader.create(
		0, View.genesis(), new AccumulatorState(0, HashUtils.zero256()), 0
	);

	@Before
	public void setup() {
		this.genesisHash = HashUtils.zero256();
		this.genesisVertex = new VerifiedVertex(UnverifiedVertex.createGenesis(MOCKED_HEADER), genesisHash);
		this.ledger = mock(Ledger.class);
		this.vertexStoreEventSender = mock(VertexStoreEventSender.class);
		this.counters = new SystemCountersImpl();
		this.bftUpdateSender = mock(BFTUpdateSender.class);
	}

	@Test
	public void creating_vertex_store_with_root_not_committed_should_fail() {
		BFTHeader genesisHeader = new BFTHeader(View.of(0), genesisHash, mock(LedgerHeader.class));
		VoteData voteData = new VoteData(genesisHeader, genesisHeader, null);
		QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		assertThatThrownBy(() ->
			VertexStore.create(
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
	public void creating_vertex_store_with_committed_qc_not_matching_vertex_should_fail() {
		BFTHeader genesisHeader = new BFTHeader(View.of(0), genesisHash, mock(LedgerHeader.class));
		BFTHeader otherHeader = new BFTHeader(View.of(0), HashUtils.random256(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(genesisHeader, genesisHeader, otherHeader);
		QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		assertThatThrownBy(() ->
			VertexStore.create(
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
	public void creating_vertex_store_with_qc_not_matching_vertex_should_fail() {
		BFTHeader genesisHeader = new BFTHeader(View.of(0), HashUtils.random256(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(genesisHeader, genesisHeader, genesisHeader);
		QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		assertThatThrownBy(() ->
			VertexStore.create(
				genesisVertex,
				badRootQC,
				ledger,
				bftUpdateSender,
				vertexStoreEventSender,
				counters
			)
		).isInstanceOf(IllegalStateException.class);
	}

}
