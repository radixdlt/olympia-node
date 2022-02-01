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
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PMT {

  private static final Logger log = LogManager.getLogger();

  private final PMTStorage db;
  private final HashFunction hashFunction;
  private final PMTNodeSerializer pmtNodeSerializer;
  private final byte[] emptyTreeHash;

  private PMTNode root;

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
    this(db, (byte[]) null, new SHA256(), new RLPSerializer());
  }

  public PMT(PMTStorage db, byte[] currentRoot) {
    this(db, currentRoot, new SHA256(), new RLPSerializer());
  }

  public PMT(PMTStorage db, HashFunction hashFunction, PMTNodeSerializer pmtNodeSerializer) {
    this(db, (byte[]) null, hashFunction, pmtNodeSerializer);
  }

  public PMT(
          PMTStorage db,
          byte[] root,
          HashFunction hashFunction,
          PMTNodeSerializer pmtNodeSerializer) {
      this(db, root == null ? null : pmtNodeSerializer.deserialize(root), hashFunction, pmtNodeSerializer);
  }

  public PMT(
      PMTStorage db,
      PMTNode root,
      HashFunction hashFunction,
      PMTNodeSerializer pmtNodeSerializer) {
    this.db = db;
    this.root = root;
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
        return new byte[0];
      } else {
        return acc.getRetVal().getValue();
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("Tree empty when key: {}", TreeUtils.toHexString(key));
      }
      return new byte[0];
    }
  }

  public PMT add(byte[] key, byte[] val) {

    try {
      var pmtKey = new PMTKey(PMTPath.intoNibbles(key));
      PMTNode newRoot;
      if (this.root != null) {
        var acc = new PMTAcc();
        this.root.insertNode(pmtKey, val, acc, this::represent, this::read);
        newRoot = acc.getTip();
        if (newRoot == null) {
          throw new IllegalStateException(
              String.format(
                  "Unexpected null PMT root when inserting key {} and value {}",
                  TreeUtils.toHexString(key),
                  TreeUtils.toHexString(val)));
        }
        for (PMTNode sanitizedAcc : acc.getNewNodes().stream().filter(Objects::nonNull).toList()) {
          byte[] serialisedNode = this.pmtNodeSerializer.serialize(sanitizedAcc);
          if (sanitizedAcc == newRoot || hasDbRepresentation(serialisedNode)) {
            this.db.save(hash(serialisedNode), serialisedNode);
          }
        }
      } else {
        newRoot = new PMTLeaf(pmtKey, val);
        byte[] serializedNewRoot = this.pmtNodeSerializer.serialize(newRoot);
        this.db.save(hash(serializedNewRoot), serializedNewRoot);
      }
      return new PMT(this.db, newRoot, this.hashFunction, this.pmtNodeSerializer);
    } catch (Exception e) {
      log.error(
          "PMT operation failure: {} for: {} {} {}",
          e.getMessage(),
          TreeUtils.toHexString(key),
          TreeUtils.toHexString(val),
          TreeUtils.toHexString(this.root == null ? emptyTreeHash : hash(this.root)));
      throw e;
    }
  }

  public byte[] getRootHash() {
    return this.root == null ? emptyTreeHash : hash(this.root);
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
