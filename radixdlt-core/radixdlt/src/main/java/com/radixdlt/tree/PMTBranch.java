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

import com.google.common.base.Objects;
import java.util.Arrays;
import java.util.function.Function;

public final class PMTBranch extends PMTNode {

  public static final int NUMBER_OF_NIBBLES = 16;

  private byte[][] children;

  record PMTBranchChild(PMTKey branchNibble, byte[] representation) {

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PMTBranchChild that = (PMTBranchChild) o;
      return Objects.equal(branchNibble, that.branchNibble)
          && Arrays.equals(representation, that.representation);
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(branchNibble);
      result = 31 * result + Arrays.hashCode(representation);
      return result;
    }

    @Override
    public String toString() {
      return "PMTBranchChild{"
          + "branchNibble="
          + branchNibble
          + ", representation="
          + Arrays.toString(representation)
          + '}';
    }
  }

  // This method is expected to mutate PMTAcc.
  public void insertNode(
      PMTKey key,
      byte[] val,
      PMTAcc acc,
      Function<PMTNode, byte[]> represent,
      Function<byte[], PMTNode> read) {
    // INFO: OLD branch always has empty key and remainder
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE) {
      handleNoRemainder(val, acc);
    } else if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NEW) {
      handleNewRemainder(key, val, acc, represent, read);
    } else {
      throw new IllegalStateException(
          String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
    }
  }

  // This method is expected to mutate PMTAcc.
  private void handleNoRemainder(byte[] val, PMTAcc acc) {
    var modifiedBranch = cloneBranch(this);
    modifiedBranch.setValue(val);
    acc.setTip(modifiedBranch);
    acc.add(modifiedBranch);
    acc.mark(this);
  }

  // This method is expected to mutate PMTAcc.
  private void handleNewRemainder(
      PMTKey key,
      byte[] val,
      PMTAcc acc,
      Function<PMTNode, byte[]> represent,
      Function<byte[], PMTNode> read) {
    var nextHash = this.getNextHash(key);
    PMTNode subTip;
    if (nextHash == null || nextHash.length == 0) {
      var newLeaf = new PMTLeaf(key.getTailNibbles(), val);
      acc.add(newLeaf);
      subTip = newLeaf;
    } else {
      var nextNode = read.apply(nextHash);
      nextNode.insertNode(key.getTailNibbles(), val, acc, represent, read);
      subTip = acc.getTip();
    }
    var branchWithNext = cloneBranch(this);
    branchWithNext.setNibble(new PMTBranchChild(key.getFirstNibble(), represent.apply(subTip)));
    acc.setTip(branchWithNext);
    acc.add(branchWithNext);
    acc.mark(this);
  }

  // This method is expected to mutate PMTAcc.
  public void getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE) {
      acc.setRetVal(this);
    } else if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NEW) {
      acc.mark(this);
      var nextHash = this.getNextHash(key);
      if (nextHash == null) {
        acc.setNotFound();
      } else {
        var nextNode = read.apply(nextHash);
        nextNode.getValue(key.getTailNibbles(), acc, read);
      }
    } else {
      throw new IllegalStateException(
          String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
    }
  }

  PMTBranch cloneBranch(PMTBranch currentBranch) {
    return currentBranch.copyForEdit();
  }

  public PMTBranch(byte[][] children, byte[] value) {
    this.children = children;
    this.value = value;
  }

  public PMTBranch(byte[] value, PMTBranchChild... nextNode) {
    this.children = new byte[NUMBER_OF_NIBBLES][];
    Arrays.fill(children, new byte[0]);
    Arrays.stream(nextNode).forEach(this::setNibble);
    if (value != null) {
      this.value = value;
    }
  }

  public byte[][] getChildren() {
    return children;
  }

  public byte[] getNextHash(PMTKey key) {
    var nib = key.getRaw()[0];
    return children[nib];
  }

  public PMTBranch setNibble(PMTBranchChild nextNode) {
    var childrenKey = nextNode.branchNibble.getFirstNibbleValue();
    this.children[childrenKey] = nextNode.representation;
    return this;
  }

  public PMTBranch copyForEdit() {
    try {
      return (PMTBranch) this.clone();
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException("Can't clone branch for edits", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    PMTBranch pmtBranch = (PMTBranch) o;

    return Arrays.deepEquals(getChildren(), pmtBranch.getChildren());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Arrays.deepHashCode(getChildren());
    return result;
  }
}
