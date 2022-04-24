/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;

/** Manages the BFT Vertex chain. TODO: Move this logic into ledger package. */
@NotThreadSafe
public final class VertexStore {

  private final EventDispatcher<BFTHighQCUpdate> highQCUpdateDispatcher;
  private final EventDispatcher<BFTInsertUpdate> bftUpdateDispatcher;
  private final EventDispatcher<BFTRebuildUpdate> bftRebuildDispatcher;
  private final EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher;

  private final Hasher hasher;
  private final Ledger ledger;

  private final Map<HashCode, PreparedVertex> vertices = new HashMap<>();
  private final Map<HashCode, Set<HashCode>> vertexChildren = new HashMap<>();

  // These should never be null
  private VerifiedVertex rootVertex;
  private QuorumCertificate highestQC;
  private QuorumCertificate highestCommittedQC;
  private Optional<TimeoutCertificate> highestTC;

  private VertexStore(
      Ledger ledger,
      Hasher hasher,
      VerifiedVertex rootVertex,
      QuorumCertificate commitQC,
      QuorumCertificate highestQC,
      EventDispatcher<BFTInsertUpdate> bftUpdateDispatcher,
      EventDispatcher<BFTRebuildUpdate> bftRebuildDispatcher,
      EventDispatcher<BFTHighQCUpdate> highQCUpdateDispatcher,
      EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher,
      Optional<TimeoutCertificate> highestTC) {
    this.ledger = Objects.requireNonNull(ledger);
    this.hasher = Objects.requireNonNull(hasher);
    this.bftUpdateDispatcher = Objects.requireNonNull(bftUpdateDispatcher);
    this.bftRebuildDispatcher = Objects.requireNonNull(bftRebuildDispatcher);
    this.highQCUpdateDispatcher = Objects.requireNonNull(highQCUpdateDispatcher);
    this.bftCommittedDispatcher = Objects.requireNonNull(bftCommittedDispatcher);
    this.rootVertex = Objects.requireNonNull(rootVertex);
    this.highestQC = Objects.requireNonNull(highestQC);
    this.highestCommittedQC = Objects.requireNonNull(commitQC);
    this.vertexChildren.put(rootVertex.getId(), new HashSet<>());
    this.highestTC = Objects.requireNonNull(highestTC);
  }

  public static VertexStore create(
      VerifiedVertexStoreState vertexStoreState,
      Ledger ledger,
      Hasher hasher,
      EventDispatcher<BFTInsertUpdate> bftUpdateDispatcher,
      EventDispatcher<BFTRebuildUpdate> bftRebuildDispatcher,
      EventDispatcher<BFTHighQCUpdate> bftHighQCUpdateDispatcher,
      EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher) {
    VertexStore vertexStore =
        new VertexStore(
            ledger,
            hasher,
            vertexStoreState.getRoot(),
            vertexStoreState.getHighQC().highestCommittedQC(),
            vertexStoreState.getHighQC().highestQC(),
            bftUpdateDispatcher,
            bftRebuildDispatcher,
            bftHighQCUpdateDispatcher,
            bftCommittedDispatcher,
            vertexStoreState.getHighQC().highestTC());

    for (VerifiedVertex vertex : vertexStoreState.getVertices()) {
      LinkedList<PreparedVertex> previous = vertexStore.getPathFromRoot(vertex.getParentId());
      Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(previous, vertex);
      if (preparedVertexMaybe.isEmpty()) {
        // Try pruning to see if that helps catching up to the ledger
        // This can occur if a node crashes between persisting a new QC and committing
        // TODO: Cleanup and remove
        VerifiedVertexStoreState pruned = vertexStoreState.prune(hasher);
        if (!pruned.equals(vertexStoreState)) {
          return create(
              pruned,
              ledger,
              hasher,
              bftUpdateDispatcher,
              bftRebuildDispatcher,
              bftHighQCUpdateDispatcher,
              bftCommittedDispatcher);
        }

        // FIXME: If this occurs then it means that our highQC may not have an associated vertex
        // FIXME: so should save preparedVertex
        break;
      } else {
        PreparedVertex preparedVertex = preparedVertexMaybe.get();
        vertexStore.vertices.put(preparedVertex.getId(), preparedVertex);
        vertexStore.vertexChildren.put(preparedVertex.getId(), new HashSet<>());
        Set<HashCode> siblings = vertexStore.vertexChildren.get(preparedVertex.getParentId());
        siblings.add(preparedVertex.getId());
      }
    }

    return vertexStore;
  }

  public VerifiedVertex getRoot() {
    return rootVertex;
  }

  public boolean tryRebuild(VerifiedVertexStoreState vertexStoreState) {

    // FIXME: Currently this assumes vertexStoreState is a chain with no forks which is our only use
    // case at the moment.
    LinkedList<PreparedVertex> prepared = new LinkedList<>();
    for (VerifiedVertex vertex : vertexStoreState.getVertices()) {
      Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(prepared, vertex);
      if (preparedVertexMaybe.isEmpty()) {
        return false;
      }

      prepared.add(preparedVertexMaybe.get());
    }

    this.rootVertex = vertexStoreState.getRoot();
    this.highestCommittedQC = vertexStoreState.getHighQC().highestCommittedQC();
    this.highestQC = vertexStoreState.getHighQC().highestQC();
    this.vertices.clear();
    this.vertexChildren.clear();
    this.vertexChildren.put(rootVertex.getId(), new HashSet<>());

    for (PreparedVertex preparedVertex : prepared) {
      this.vertices.put(preparedVertex.getId(), preparedVertex);
      this.vertexChildren.put(preparedVertex.getId(), new HashSet<>());
      Set<HashCode> siblings = vertexChildren.get(preparedVertex.getParentId());
      siblings.add(preparedVertex.getId());
    }

    bftRebuildDispatcher.dispatch(BFTRebuildUpdate.create(vertexStoreState));
    return true;
  }

  public boolean containsVertex(HashCode vertexId) {
    return vertices.containsKey(vertexId) || rootVertex.getId().equals(vertexId);
  }

  public void insertVertexChain(VerifiedVertexChain verifiedVertexChain) {
    for (VerifiedVertex v : verifiedVertexChain.getVertices()) {
      if (!addQC(v.getQC())) {
        return;
      }

      insertVertex(v);
    }
  }

  public boolean addQC(QuorumCertificate qc) {
    if (!this.containsVertex(qc.getProposed().getVertexId())) {
      return false;
    }

    if (!vertexChildren.get(qc.getProposed().getVertexId()).isEmpty()) {
      // TODO: Check to see if qc's match in case there's a fault
      return true;
    }

    boolean isHighQC = qc.getView().gt(highestQC.getView());
    boolean isHighCommit = qc.getCommittedAndLedgerStateProof(hasher).isPresent();
    if (!isHighQC && !isHighCommit) {
      return true;
    }

    if (isHighQC) {
      highestQC = qc;
    }

    if (isHighCommit) {
      qc.getCommitted().ifPresent(header -> this.commit(header, qc));
    } else {
      // TODO: we lose all other tail QCs on this save, Not sure if this is okay...investigate...
      VerifiedVertexStoreState vertexStoreState = getState();
      this.highQCUpdateDispatcher.dispatch(BFTHighQCUpdate.create(vertexStoreState));
    }

    return true;
  }

  private void getChildrenVerticesList(
      VerifiedVertex parent, ImmutableList.Builder<VerifiedVertex> builder) {
    Set<HashCode> childrenIds = this.vertexChildren.get(parent.getId());
    if (childrenIds == null) {
      return;
    }

    for (HashCode childId : childrenIds) {
      VerifiedVertex v = vertices.get(childId).getVertex();
      builder.add(v);
      getChildrenVerticesList(v, builder);
    }
  }

  private VerifiedVertexStoreState getState() {
    // TODO: store list dynamically rather than recomputing
    ImmutableList.Builder<VerifiedVertex> verticesBuilder = ImmutableList.builder();
    getChildrenVerticesList(this.rootVertex, verticesBuilder);
    return VerifiedVertexStoreState.create(
        this.highQC(), this.rootVertex, verticesBuilder.build(), this.highestTC, hasher);
  }

  /**
   * Inserts a timeout certificate into the store.
   *
   * @param timeoutCertificate the timeout certificate
   */
  public void insertTimeoutCertificate(TimeoutCertificate timeoutCertificate) {
    if (this.highestTC.isEmpty()
        || this.highestTC.get().getView().lt(timeoutCertificate.getView())) {
      this.highestTC = Optional.of(timeoutCertificate);
    }
  }

  /**
   * Returns the highest inserted timeout certificate.
   *
   * @return the highest inserted timeout certificate
   */
  public Optional<TimeoutCertificate> getHighestTimeoutCertificate() {
    return this.highestTC;
  }

  /**
   * Returns the vertex with specified id or empty if not exists.
   *
   * @param id the id of a vertex
   * @return the specified vertex or empty
   */
  // TODO: reimplement in async way
  public Optional<PreparedVertex> getPreparedVertex(HashCode id) {
    return Optional.ofNullable(vertices.get(id));
  }

  /**
   * Inserts a vertex and then attempts to create the next header.
   *
   * @param vertex vertex to insert
   */
  public void insertVertex(VerifiedVertex vertex) {
    PreparedVertex v = vertices.get(vertex.getId());
    if (v != null) {
      return;
    }

    if (!this.containsVertex(vertex.getParentId())) {
      throw new MissingParentException(vertex.getParentId());
    }

    insertVertexInternal(vertex);
  }

  private void insertVertexInternal(VerifiedVertex vertex) {
    LinkedList<PreparedVertex> previous = getPathFromRoot(vertex.getParentId());
    Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(previous, vertex);
    preparedVertexMaybe.ifPresent(
        preparedVertex -> {
          vertices.put(preparedVertex.getId(), preparedVertex);
          vertexChildren.put(preparedVertex.getId(), new HashSet<>());
          Set<HashCode> siblings = vertexChildren.get(preparedVertex.getParentId());
          siblings.add(preparedVertex.getId());

          VerifiedVertexStoreState vertexStoreState = getState();
          BFTInsertUpdate update =
              BFTInsertUpdate.insertedVertex(preparedVertex, siblings.size(), vertexStoreState);
          bftUpdateDispatcher.dispatch(update);
        });
  }

  private void removeVertexAndPruneInternal(HashCode vertexId, HashCode skip) {
    vertices.remove(vertexId);

    if (this.rootVertex.getId().equals(vertexId)) {
      return;
    }

    var children = vertexChildren.remove(vertexId);
    for (HashCode child : children) {
      if (!child.equals(skip)) {
        removeVertexAndPruneInternal(child, null);
      }
    }
  }

  /**
   * Commit a vertex. Executes the atom and prunes the tree.
   *
   * @param header the header to be committed
   * @param commitQC the proof of commit
   */
  private void commit(BFTHeader header, QuorumCertificate commitQC) {
    if (header.getView().compareTo(this.rootVertex.getView()) <= 0) {
      return;
    }

    final HashCode vertexId = header.getVertexId();
    final VerifiedVertex tipVertex = vertices.get(vertexId).getVertex();
    if (tipVertex == null) {
      throw new IllegalStateException("Committing vertex not in store: " + header);
    }

    this.rootVertex = tipVertex;
    this.highestCommittedQC = commitQC;
    final var path = ImmutableList.copyOf(getPathFromRoot(tipVertex.getId()));
    HashCode prev = null;
    for (int i = path.size() - 1; i >= 0; i--) {
      this.removeVertexAndPruneInternal(path.get(i).getId(), prev);
      prev = path.get(i).getId();
    }

    VerifiedVertexStoreState vertexStoreState = getState();
    this.bftCommittedDispatcher.dispatch(new BFTCommittedUpdate(path, vertexStoreState));
  }

  public LinkedList<PreparedVertex> getPathFromRoot(HashCode vertexId) {
    final LinkedList<PreparedVertex> path = new LinkedList<>();

    PreparedVertex vertex = vertices.get(vertexId);
    while (vertex != null) {
      path.addFirst(vertex);
      vertex = vertices.get(vertex.getParentId());
    }

    return path;
  }

  /**
   * Retrieves the highest QC and highest committed QC in the store.
   *
   * @return the highest QCs
   */
  public HighQC highQC() {
    return HighQC.from(this.highestQC, this.highestCommittedQC, this.highestTC);
  }

  /**
   * Retrieves list of vertices starting with the given vertexId and then proceeding to its
   * ancestors.
   *
   * <p>if the store does not contain some vertex then will return an empty list.
   *
   * @param vertexId the id of the vertex
   * @param count the number of vertices to retrieve
   * @return the list of vertices if all found, otherwise an empty list
   */
  public Optional<ImmutableList<VerifiedVertex>> getVertices(HashCode vertexId, int count) {
    HashCode nextId = vertexId;
    ImmutableList.Builder<VerifiedVertex> builder = ImmutableList.builderWithExpectedSize(count);
    for (int i = 0; i < count; i++) {
      final VerifiedVertex verifiedVertex;
      if (nextId.equals(rootVertex.getId())) {
        verifiedVertex = rootVertex;
      } else if (this.vertices.containsKey(nextId)) {
        final PreparedVertex preparedVertex = this.vertices.get(nextId);
        verifiedVertex = preparedVertex.getVertex();
      } else {
        return Optional.empty();
      }

      builder.add(verifiedVertex);
      nextId = verifiedVertex.getParentId();
    }

    return Optional.of(builder.build());
  }
}
