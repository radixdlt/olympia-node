package com.radixdlt.consensus.liveness;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.mempool.Mempool;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Logic for generating new proposals
 */
public final class ProposalGenerator {
	private final Mempool mempool;
	private final VertexStore vertexStore;

	@Inject
	public ProposalGenerator(
		VertexStore vertexStore,
		Mempool mempool
	) {
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.mempool = Objects.requireNonNull(mempool);
	}

	// TODO: check that next proposal works with current vertexStore state
	public Vertex generateProposal(View view) {
		final QuorumCertificate highestQC = vertexStore.getHighestQC();
		final List<Vertex> preparedVertices = vertexStore.getPathFromRoot(highestQC.getVertexMetadata().getId());
		final Set<AID> preparedAtoms = preparedVertices.stream()
			.map(Vertex::getAtom)
			.map(Atom::getAID)
			.collect(Collectors.toSet());

		final List<Atom> atoms = mempool.getAtoms(1, preparedAtoms);

		return Vertex.createVertex(highestQC, view, !atoms.isEmpty() ? atoms.get(0) : null);
	}
}
