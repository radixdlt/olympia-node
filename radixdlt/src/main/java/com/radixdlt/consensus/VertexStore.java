package com.radixdlt.consensus;

import java.util.HashSet;

/**
 * Manages the BFT Vertex chain
 */
public final class VertexStore {
	private final HashSet<Vertex> vertices = new HashSet<>();
	private QuorumCertificate highestQC = null;

	public VertexStore() {
	}

	public void syncToQC(QuorumCertificate qc) {
		if (qc == null) {
			return;
		}

		if (highestQC == null || highestQC.getRound() < qc.getRound()) {
			highestQC = qc;
		}
	}

	public void insertVertex(Vertex vertex) {
		this.syncToQC(vertex.getQc());
		vertices.add(vertex);
	}

	public QuorumCertificate getHighestQC() {
		return highestQC;
	}
}
