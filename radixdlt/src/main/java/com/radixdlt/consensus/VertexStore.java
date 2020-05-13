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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;

import com.radixdlt.middleware2.LedgerAtom;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.events.Events;
import org.radix.validation.ConstraintMachineValidationException;

/**
 * Manages the BFT Vertex chain.
 *
 * In general this class is NOT thread-safe except for getVertex() and getHighestQC().
 * TODO: make thread-safe
 */
public final class VertexStore {
	private final RadixEngine<LedgerAtom> engine;
	private final SystemCounters counters;
	private final Map<Hash, Vertex> vertices = new ConcurrentHashMap<>();
	private final BehaviorSubject<Vertex> lastCommittedVertex = BehaviorSubject.create();

	// Should never be null
	private Vertex root;

	// Should never be null
	private volatile QuorumCertificate highestQC;

	// TODO: Cleanup this interface
	public VertexStore(
		Vertex genesisVertex,
		QuorumCertificate rootQC,
		RadixEngine<LedgerAtom> engine,
		SystemCounters counters
	) {
		this.engine = Objects.requireNonNull(engine);
		this.counters = Objects.requireNonNull(counters);
		this.highestQC = Objects.requireNonNull(rootQC);
		try {
			this.engine.checkAndStore(genesisVertex.getAtom());
		} catch (RadixEngineException e) {
			throw new IllegalStateException("Could not store genesis atom: " + genesisVertex.getAtom(), e);
		}
		this.vertices.put(genesisVertex.getId(), genesisVertex);
		this.root = genesisVertex;
		this.lastCommittedVertex.onNext(genesisVertex);
	}

	public boolean syncToQC(QuorumCertificate qc) {
		final Vertex vertex = vertices.get(qc.getProposed().getId());
		if (vertex != null) {
			addQC(qc);
			return true;
		}

		return false;
	}

	public void addQC(QuorumCertificate qc) {
		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
		}
	}

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		final Vertex parent = vertices.get(vertex.getParentId());
		if (parent == null) {
			throw new MissingParentException(vertex.getParentId());
		}

		// TODO: Don't check for state computer errors for now so that we don't
		// TODO: have to deal with failing leader proposals
		// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
		// TODO: (also see commitVertex->storeAtom)

		vertices.put(vertex.getId(), vertex);
		updateVertexStoreSize();
	}

	public Vertex commitVertex(Hash vertexId) {
		final Vertex tipVertex = vertices.get(vertexId);
		if (tipVertex == null) {
			throw new IllegalStateException("Committing a vertex which was never inserted: " + vertexId);
		}
		final LinkedList<Vertex> path = new LinkedList<>();
		Vertex vertex = tipVertex;
		while (vertex != null && !root.equals(vertex)) {
			path.addFirst(vertex);
			vertex = vertices.remove(vertex.getParentId());
		}

		for (Vertex committed : path) {
			if (committed.getAtom() != null) {
				storeAtom(committed.getAtom());
			}

			lastCommittedVertex.onNext(committed);
		}

		vertices.remove(root.getId());
		root = tipVertex;

		updateVertexStoreSize();
		return tipVertex;
	}

	private void storeAtom(LedgerAtom atom) {
		try {
			this.counters.increment(CounterType.LEDGER_PROCESSED);
			this.engine.checkAndStore(atom);
			this.counters.increment(CounterType.LEDGER_STORED);
		} catch (RadixEngineException e) {
			// TODO: Don't check for state computer errors for now so that we don't
			// TODO: have to deal with failing leader proposals
			// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals

			// TODO: move VIRTUAL_STATE_CONFLICT to static check
			if (e.getErrorCode() == RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT) {
				ConstraintMachineValidationException exception
					= new ConstraintMachineValidationException(atom, "Virtual state conflict", e.getDataPointer());
				Events.getInstance().broadcast(new AtomExceptionEvent(exception, atom.getAID()));
			} else if (e.getErrorCode() == RadixEngineErrorCode.STATE_CONFLICT) {
				final ParticleConflictException conflict = new ParticleConflictException(
					new ParticleConflict(e.getDataPointer(), ImmutableSet.of(atom.getAID(), e.getRelated().getAID())
				));
				AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(conflict, atom.getAID());
				Events.getInstance().broadcast(atomExceptionEvent);
			} else if (e.getErrorCode() == RadixEngineErrorCode.MISSING_DEPENDENCY) {
				final AtomDependencyNotFoundException notFoundException =
					new AtomDependencyNotFoundException(
						String.format("Atom has missing dependencies in transitions: %s", e.getDataPointer().toString()),
						e.getDataPointer()
					);

				AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(notFoundException, atom.getAID());
				Events.getInstance().broadcast(atomExceptionEvent);
			}
		}
	}

	public Observable<Vertex> lastCommittedVertex() {
		return lastCommittedVertex;
	}

	public List<Vertex> getPathFromRoot(Hash vertexId) {
		final List<Vertex> path = new ArrayList<>();

		Vertex vertex = vertices.get(vertexId);
		while (vertex != null && !vertex.getId().equals(root.getId())) {
			path.add(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest qc in the store
	 * Thread-safe.
	 *
	 * @return the highest quorum certificate
	 */
	public QuorumCertificate getHighestQC() {
		return this.highestQC;
	}

	/**
	 * Retrieves the vertex with the given vertexId if it exists in the store.
	 * Thread-safe.
	 *
	 * @param vertexId the id of the vertex
	 * @return the vertex or null, if it is not stored
	 */
	public Vertex getVertex(Hash vertexId) {
		return this.vertices.get(vertexId);
	}

	public int getSize() {
		return vertices.size();
	}

	private void updateVertexStoreSize() {
		this.counters.set(CounterType.CONSENSUS_VERTEXSTORE_SIZE, this.vertices.size());
	}
}
