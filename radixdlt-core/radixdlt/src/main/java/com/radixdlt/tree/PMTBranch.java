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
  public static final String UNEXPECTED_SUBTREE_ERROR_MSG = "Unexpected subtree: %s";

  private byte[][] children;

  public PMTBranch(PMTBranch pmtBranch) {
    super(
        null,
        pmtBranch.getValue() == null
            ? null
            : Arrays.copyOfRange(pmtBranch.value, 0, pmtBranch.getValue().length));
    this.children = new byte[NUMBER_OF_NIBBLES][];
    for (int i = 0; i < NUMBER_OF_NIBBLES; i++) {
      this.children[i] = Arrays.copyOfRange(pmtBranch.children[i], 0, pmtBranch.children[i].length);
    }
  }

  public PMTBranch(byte[][] children, byte[] value) {
    super(null, value);
    this.children = children;
  }

  public PMTBranch(byte[] value, PMTBranchChild... nextNode) {
    super(null, value);
    this.children = new byte[NUMBER_OF_NIBBLES][];
    Arrays.fill(children, new byte[0]);
    Arrays.stream(nextNode).forEach(this::setNibble);
  }

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
          String.format(UNEXPECTED_SUBTREE_ERROR_MSG, commonPath.whichRemainderIsLeft()));
    }
  }

  @Override
  public void removeNode(
      PMTKey key, PMTAcc acc, Function<PMTNode, byte[]> represent, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE) {
      if (branchDoesNotHaveValue()) {
        acc.setNotFound();
        return;
      } else {
        acc.remove(this);
        var newBranch = new PMTBranch(this);
        newBranch.setValue(null);
        acc.add(newBranch);
        acc.setTip(newBranch);
      }
    } else if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NEW) {
      var nextHash = this.getNextHash(key);
      if (nextHash == null) {
        acc.setNotFound();
        return;
      }
      PMTNode currentChild = read.apply(nextHash);
      currentChild.removeNode(key.getTailNibbles(), acc, represent, read);
      PMTNode newChild = acc.getTip();
      if (acc.notFound()) {
        acc.setTip(null);
      } else if (newChild == null) { // remove child
        removeBranchChild(key, acc, read);
      } else { // update child
        updateBranchChild(key, acc, represent, newChild);
      }
    } else {
      throw new IllegalStateException(
          String.format(UNEXPECTED_SUBTREE_ERROR_MSG, commonPath.whichRemainderIsLeft()));
    }
  }

  private void removeBranchChild(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
    if (numberOfChildren() == 1L) {
      handleBranchWithOnlyOneChild(acc);
    } else if (numberOfChildren() == 2L && (branchDoesNotHaveValue())) {
      handleBranchWithTwoChildrenAndNoValue(acc, read, key);
    } else {
      acc.remove(this);
      var branchWithNext = new PMTBranch(this);
      branchWithNext.setEmptyChild(key.getFirstNibble());
      acc.add(branchWithNext);
      acc.setTip(branchWithNext);
    }
  }

  private void handleBranchWithOnlyOneChild(PMTAcc acc) {
    if (branchDoesNotHaveValue()) {
      acc.remove(this);
      acc.setTip(null);
    } else {
      acc.remove(this);
      var newLeaf = new PMTLeaf(new PMTKey(new byte[0]), this.getValue());
      acc.add(newLeaf);
      acc.setTip(newLeaf);
    }
  }

  private void handleBranchWithTwoChildrenAndNoValue(
      PMTAcc acc, Function<byte[], PMTNode> read, PMTKey key) {
    byte[] otherChildHashPointer = new byte[0];
    var index = 0;
    for (; index < children.length; index++) {
      otherChildHashPointer = children[index];
      if (otherChildHashPointer != null
          && otherChildHashPointer.length > 0
          && index != key.getFirstNibbleValue()) {
        break;
      }
    }
    var otherChild = read.apply(otherChildHashPointer);
    acc.remove(this);
    var newNode =
        switch (otherChild) {
          case PMTLeaf pmtLeaf -> new PMTLeaf(
              new PMTKey(new byte[] {(byte) index}).concatenate(pmtLeaf.getKey()),
              pmtLeaf.getValue());
          case PMTBranch ignored -> new PMTExt(
              new PMTKey(new byte[] {(byte) index}), otherChildHashPointer);
          case PMTExt pmtExt -> new PMTExt(
              new PMTKey(new byte[] {(byte) index}).concatenate(pmtExt.getKey()),
              pmtExt.getValue());
        };
    acc.add(newNode);
    acc.setTip(newNode);
  }

  private boolean branchDoesNotHaveValue() {
    return this.getValue() == null || this.getValue().length == 0;
  }

  private void updateBranchChild(
      PMTKey key, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTNode newChild) {
    acc.remove(this);
    var branchWithNext = new PMTBranch(this);
    branchWithNext.setNibble(new PMTBranchChild(key.getFirstNibble(), represent.apply(newChild)));
    acc.add(branchWithNext);
    acc.setTip(branchWithNext);
  }

  private long numberOfChildren() {
    return Arrays.stream(children).filter(it -> it != null && it.length > 0).count();
  }

  // This method is expected to mutate PMTAcc.
  private void handleNoRemainder(byte[] val, PMTAcc acc) {
    var modifiedBranch = new PMTBranch(this);
    modifiedBranch.setValue(val);
    acc.setTip(modifiedBranch);
    acc.add(modifiedBranch);
    acc.mark(this);
    acc.remove(this);
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
    var branchWithNext = new PMTBranch(this);
    branchWithNext.setNibble(new PMTBranchChild(key.getFirstNibble(), represent.apply(subTip)));
    acc.setTip(branchWithNext);
    acc.add(branchWithNext);
    acc.mark(this);
    acc.remove(this);
  }

  // This method is expected to mutate PMTAcc.
  public void getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE) {
      if (this.getValue() != null && this.getValue().length > 0) {
        acc.setRetVal(this);
      } else {
        acc.setNotFound();
      }
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
          String.format(UNEXPECTED_SUBTREE_ERROR_MSG, commonPath.whichRemainderIsLeft()));
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

  private void setEmptyChild(PMTKey childKey) {
    this.children[childKey.getFirstNibbleValue()] = new byte[0];
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
