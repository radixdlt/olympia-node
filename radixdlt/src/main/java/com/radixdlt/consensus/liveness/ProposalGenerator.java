package com.radixdlt.consensus.liveness;

import com.google.common.collect.Sets;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Round;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.mempool.Mempool;
import java.util.List;
import java.util.Objects;

/**
 * Logic for generating new proposals
 */
public final class ProposalGenerator {
	private final Mempool mempool;
	private final VertexStore vertexStore;

	@com.google.inject.Inject
	public ProposalGenerator(
		VertexStore vertexStore,
		Mempool mempool
	) {
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.mempool = Objects.requireNonNull(mempool);
	}

	// TODO: check that next proposal works with current vertexStore state
	public Vertex generateProposal(Round round) {
		QuorumCertificate highestQC = vertexStore.getHighestQC();

		List<Atom> atoms = mempool.getAtoms(1, Sets.newHashSet());

		return Vertex.createVertex(highestQC, round, !atoms.isEmpty() ? atoms.get(0) : null);
	}
}
