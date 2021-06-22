/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

/**
 * Vertex in a Vertex graph
 */
@Immutable
@SerializerId2("consensus.vertex")
public final class UnverifiedVertex {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate qc;

	private final View view;

	@JsonProperty("txns")
	@DsonOutput(Output.ALL)
	private final List<byte[]> txns;

	@JsonProperty("tout")
	@DsonOutput(Output.ALL)
	private final Boolean proposerTimedOut;

	private final BFTNode proposer;

	@JsonCreator
	UnverifiedVertex(
		@JsonProperty("qc") QuorumCertificate qc,
		@JsonProperty("view") Long viewId,
		@JsonProperty("txns") List<byte[]> txns,
		@JsonProperty("p") byte[] proposer,
		@JsonProperty("tout") Boolean proposerTimedOut
	) throws PublicKeyException {
		this(
			qc,
			viewId != null ? View.of(viewId) : null,
			txns == null ? List.of() : txns,
			proposer != null ? BFTNode.fromPublicKeyBytes(proposer) : null,
			proposerTimedOut
		);
	}

	public UnverifiedVertex(QuorumCertificate qc, View view, List<byte[]> txns, BFTNode proposer, Boolean proposerTimedOut) {
		this.qc = Objects.requireNonNull(qc);
		this.view = Objects.requireNonNull(view);

		if (proposerTimedOut != null && proposerTimedOut && !txns.isEmpty()) {
			throw new IllegalArgumentException("Txns must be empty if timeout");
		}

		this.txns = txns;
		this.proposer = proposer;
		this.proposerTimedOut = proposerTimedOut;
	}

	public static UnverifiedVertex createGenesis(LedgerHeader ledgerHeader) {
		BFTHeader header = BFTHeader.ofGenesisAncestor(ledgerHeader);
		final VoteData voteData = new VoteData(header, header, header);
		final QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		return new UnverifiedVertex(qc, View.genesis(), null, null, false);
	}

	public static UnverifiedVertex createTimeout(QuorumCertificate qc, View view, BFTNode proposer) {
		return new UnverifiedVertex(qc, view, List.of(), proposer, true);
	}

	public static UnverifiedVertex create(
		QuorumCertificate qc,
		View view,
		List<Txn> txns,
		BFTNode proposer
	) {
		Objects.requireNonNull(qc);

		if (view.number() == 0) {
			throw new IllegalArgumentException("Only genesis can have view 0.");
		}

		var txnBytes = txns.stream().map(Txn::getPayload).collect(Collectors.toList());

		return new UnverifiedVertex(qc, view, txnBytes, proposer, false);
	}

	@JsonProperty("p")
	@DsonOutput(Output.ALL)
	private byte[] getProposerJson() {
		return proposer == null ? null : proposer.getKey().getCompressedBytes();
	}

	public BFTNode getProposer() {
		return proposer;
	}

	public boolean isTimeout() {
		return proposerTimedOut != null && proposerTimedOut;
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	public View getView() {
		return view;
	}

	public List<Txn> getTxns() {
		return txns == null ? List.of() : txns.stream().map(Txn::create).collect(Collectors.toList());
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@Override
	public String toString() {
		return String.format("Vertex{view=%s, qc=%s, txns=%s}", view, qc, getTxns());
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, proposer, view, txns, proposerTimedOut);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UnverifiedVertex)) {
			return false;
		}

		UnverifiedVertex v = (UnverifiedVertex) o;
		return Objects.equals(v.view, this.view)
			&& Objects.equals(v.proposerTimedOut, this.proposerTimedOut)
			&& Objects.equals(v.proposer, this.proposer)
			&& Objects.equals(v.getTxns(), this.getTxns())
			&& Objects.equals(v.qc, this.qc);
	}
}
