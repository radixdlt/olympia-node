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

package com.radixdlt.tree;

import com.radixdlt.tree.hash.HashFunction;
import com.radixdlt.tree.hash.SHA256;
import com.radixdlt.tree.serialization.PMTNodeSerializer;
import com.radixdlt.tree.serialization.rlp.RLPSerializer;
import com.radixdlt.tree.storage.PMTStorage;
import com.radixdlt.utils.Pair;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PMT {

  private static final Logger log = LogManager.getLogger();

  private static final RLPSerializer RLP_SERIALIZER = new RLPSerializer();
  private static final SHA256 SHA_256 = new SHA256();

  private final PMTStorage db;
  private final HashFunction hashFunction;
  private final PMTNodeSerializer pmtNodeSerializer;
  private final byte[] emptyTreeHash;

  private final PMTNode root;
  private final byte[] serializedRoot;
  private final byte[] rootHash;

  // API:
  // add
  // update
  // delete
  // get
  //
  // get_root
  // get_proof
  // verify_proof

  public PMT(PMTStorage db) {
    this(db, null);
  }

  public PMT(PMTStorage db, byte[] rootHash) {
    this(
        db,
        rootHash == null ? null : RLP_SERIALIZER.deserialize(db.read(rootHash)),
        SHA_256,
        RLP_SERIALIZER);
  }

  public PMT(PMTStorage db, HashFunction hashFunction, PMTNodeSerializer pmtNodeSerializer) {
    this(db, (byte[]) null, hashFunction, pmtNodeSerializer);
  }

  public PMT(
      PMTStorage db,
      byte[] rootHash,
      HashFunction hashFunction,
      PMTNodeSerializer pmtNodeSerializer) {
    this(
        db,
        rootHash == null ? null : pmtNodeSerializer.deserialize(db.read(rootHash)),
        hashFunction,
        pmtNodeSerializer);
  }

  private PMT(
      PMTStorage db, PMTNode root, HashFunction hashFunction, PMTNodeSerializer pmtNodeSerializer) {
    this.db = db;
    this.root = root;
    this.serializedRoot = this.root == null ? null : pmtNodeSerializer.serialize(this.root);
    this.rootHash = this.serializedRoot == null ? null : hashFunction.hash(this.serializedRoot);
    this.hashFunction = hashFunction;
    this.pmtNodeSerializer = pmtNodeSerializer;
    this.emptyTreeHash = hashFunction.hash(pmtNodeSerializer.emptyTree());
  }

  public byte[] represent(PMTNode node) {
    return represent(node, this.hashFunction, this.pmtNodeSerializer);
  }

  public static byte[] represent(
      PMTNode node, HashFunction hashFunction, PMTNodeSerializer pmtNodeSerializer) {
    var ser = pmtNodeSerializer.serialize(node);
    if (hasDbRepresentation(ser)) {
      return hashFunction.hash(ser);
    } else {
      return ser;
    }
  }

  public static boolean hasDbRepresentation(byte[] serializedNode) {
    // INFO: the DB_SIZE_CONDITION relates to hash size
    return serializedNode.length >= PMTNode.DB_SIZE_COND;
  }

  public byte[] get(byte[] key) {
    var pmtKey = new PMTKey(PMTPath.intoNibbles(key));

    if (this.root != null) {
      var acc = new PMTAcc();
      this.root.getValue(pmtKey, acc, this::read);
      if (acc.notFound()) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Not found key: {} for root: {}",
              TreeUtils.toHexString(key),
              TreeUtils.toHexString(represent(this.root)));
        }
        return null;
      } else {
        return acc.getRetVal().getValue();
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("Tree empty when key: {}", TreeUtils.toHexString(key));
      }
      return null;
    }
  }

  public PMT addAll(List<Pair<byte[], byte[]>> values) {
    byte[] key = new byte[0];
    byte[] val = new byte[0];
    try {
      var pmt = this;
      for (var entry : values) {
        key = entry.getFirst();
        val = entry.getSecond();
        pmt = insertKeyValue(key, val, pmt);
      }
      return pmt;
    } catch (Exception e) {
      throw new IllegalStateException(
          String.format(
              "An unexpected error happened when inserting key %s and value %s in PMT with root"
                  + " %s.",
              TreeUtils.toHexString(key),
              TreeUtils.toHexString(val),
              TreeUtils.toHexString(this.root == null ? emptyTreeHash : hash(this.root))),
          e);
    }
  }

  private PMT insertKeyValue(byte[] key, byte[] val, PMT pmt) {
    PMTNode newRoot;
    var pmtKey = new PMTKey(PMTPath.intoNibbles(key));
    if (pmt.root != null) {
      var acc = new PMTAcc();
      pmt.root.insertNode(pmtKey, val, acc, this::represent, this::read);
      newRoot = acc.getTip();
      if (newRoot == null) {
        throw new IllegalStateException(
            String.format(
                "Unexpected null PMT root when inserting key %s and value %s",
                TreeUtils.toHexString(key), TreeUtils.toHexString(val)));
      }
      saveNewNodesToDB(acc.getNewNodes().stream().filter(Objects::nonNull).toList());
      removeStaleNodesFromDB(acc.getRemovedNodes().stream().filter(Objects::nonNull).toList());
    } else {
      newRoot = new PMTLeaf(pmtKey, val);
    }
    this.save(pmtNodeSerializer.serialize(newRoot));
    return new PMT(this.db, newRoot, this.hashFunction, this.pmtNodeSerializer);
  }

  public PMT add(byte[] key, byte[] val) {
    return this.addAll(List.of(Pair.of(key, val)));
  }

  public PMT remove(byte[] key) {
    if (this.root != null) {
      var pmtKey = new PMTKey(PMTPath.intoNibbles(key));
      var acc = new PMTAcc();
      this.root.removeNode(pmtKey, acc, this::represent, this::read);
      if (acc.notFound()) {
        return this;
      } else {
        PMTNode newRoot = acc.getTip();
        saveNewNodesToDB(acc.getNewNodes().stream().filter(Objects::nonNull).toList());
        removeStaleNodesFromDB(acc.getRemovedNodes().stream().filter(Objects::nonNull).toList());
        if (newRoot != null) {
          this.save(pmtNodeSerializer.serialize(newRoot));
        }
        return new PMT(this.db, newRoot, this.hashFunction, this.pmtNodeSerializer);
      }
    } else {
      return null;
    }
  }

  private void saveNewNodesToDB(List<PMTNode> nodes) {
    applyDBOperation(nodes, this::save);
  }

  private void removeStaleNodesFromDB(List<PMTNode> nodes) {
    applyDBOperation(nodes, this::delete);
  }

  private void applyDBOperation(List<PMTNode> nodes, Consumer<byte[]> dbOperation) {
    for (PMTNode node : nodes) {
      byte[] serialisedNode = this.pmtNodeSerializer.serialize(node);
      if (hasDbRepresentation(serialisedNode)) {
        dbOperation.accept(serialisedNode);
      }
    }
  }

  private void save(byte[] serialisedNode) {
    this.db.save(hash(serialisedNode), serialisedNode);
  }

  private void delete(byte[] serializedNode) {
    this.db.delete(hash(serializedNode));
  }

  public byte[] getRootHash() {
    return this.root == null ? emptyTreeHash : this.rootHash;
  }

  private PMTNode read(byte[] key) {
    byte[] nodeBytes;
    if (hasDbRepresentation(key)) {
      nodeBytes = this.db.read(key);
    } else {
      nodeBytes = key;
    }
    return pmtNodeSerializer.deserialize(nodeBytes);
  }

  private byte[] hash(byte[] serialized) {
    return this.hashFunction.hash(serialized);
  }

  private byte[] hash(PMTNode node) {
    return hash(this.pmtNodeSerializer.serialize(node));
  }
}
