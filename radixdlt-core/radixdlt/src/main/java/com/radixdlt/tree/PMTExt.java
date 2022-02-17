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

import java.util.function.Function;

public final class PMTExt extends PMTNode {

  public static final int EVEN_PREFIX = 0;
  public static final int ODD_PREFIX = 1;
  public static final String UNEXPECTED_SUBTREE_ERROR_MSG = "Unexpected subtree: %s";

  public PMTExt(PMTKey keyNibbles, byte[] newHashPointer) {
    super(keyNibbles, newHashPointer);
    if (keyNibbles.isEmpty()) {
      throw new IllegalArgumentException("Extensions must have non empty key-part");
    }
  }

  // This method is expected to mutate PMTAcc.
  public void insertNode(
      PMTKey key,
      byte[] val,
      PMTAcc acc,
      Function<PMTNode, byte[]> represent,
      Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    switch (commonPath.whichRemainderIsLeft()) {
      case EXISTING:
        handleOnlyExistingRemainderIsLeft(val, acc, commonPath, represent);
        break;
      case NEW, NONE:
        handleNoRemainderIsLeft(val, acc, commonPath, represent, read);
        break;
      case EXISTING_AND_NEW:
        handleBothExistingAndNewRemainders(val, acc, commonPath, represent);
        break;
      default:
        throw new IllegalStateException(
            String.format(UNEXPECTED_SUBTREE_ERROR_MSG, commonPath.whichRemainderIsLeft()));
    }
  }

  @Override
  public void removeNode(
      PMTKey key, PMTAcc acc, Function<PMTNode, byte[]> represent, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    if (commonPath.getCommonPrefix().isEmpty()) {
      acc.setNotFound();
    } else if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE
        || commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NEW) {
      PMTNode currentChild;
      currentChild = read.apply(getValue());
      var newRemainder = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
      currentChild.removeNode(newRemainder, acc, represent, read);
      PMTNode newChild = acc.getTip();
      if (acc.notFound()) {
        acc.setTip(null);
      } else if (newChild == null) { // remove child
        acc.remove(this);
        acc.setTip(null);
      } else { // update child
        acc.remove(this);
        PMTNode newNode =
            switch (newChild) {
              case PMTLeaf pmtLeaf -> {
                acc.removeFromAddedAcc(pmtLeaf);
                yield new PMTLeaf(this.getKey().concatenate(pmtLeaf.getKey()), pmtLeaf.getValue());
              }
              case PMTBranch pmtBranch -> new PMTExt(this.getKey(), represent.apply(pmtBranch));
              case PMTExt pmtExt -> {
                acc.removeFromAddedAcc(pmtExt);
                yield new PMTExt(
                        this.getKey().concatenate(pmtExt.getKey()), pmtExt.getValue());
              }

            };
        acc.add(newNode);
        acc.setTip(newNode);
      }
    } else {
      throw new IllegalStateException(
          String.format(UNEXPECTED_SUBTREE_ERROR_MSG, commonPath.whichRemainderIsLeft()));
    }
  }

  // This method is expected to mutate PMTAcc.
  private void handleBothExistingAndNewRemainders(
      byte[] val, PMTAcc acc, PMTPath commonPath, Function<PMTNode, byte[]> represent) {
    var remainderOld = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
    PMTKey remainderNew = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
    byte[] sliceVal;
    // INFO: as implemented here, the key-end can be empty
    var newLeaf = new PMTLeaf(remainderNew.getTailNibbles(), val);
    if (remainderOld.isNibble()) {
      sliceVal = this.getValue();
    } else {
      var newShorter = new PMTExt(remainderOld.getTailNibbles(), this.getValue());
      acc.add(newShorter);
      sliceVal = represent.apply(newShorter);
    }
    PMTBranch newBranch =
        new PMTBranch(
            null,
            new PMTBranch.PMTBranchChild(remainderNew.getFirstNibble(), represent.apply(newLeaf)),
            new PMTBranch.PMTBranchChild(remainderOld.getFirstNibble(), sliceVal));
    computeAndSetTip(commonPath, newBranch, acc, represent);
    acc.add(newBranch, newLeaf);
    acc.mark(this);
    acc.remove(this);
  }

  // This method is expected to mutate PMTAcc.
  private void handleNoRemainderIsLeft(
      byte[] val,
      PMTAcc acc,
      PMTPath commonPath,
      Function<PMTNode, byte[]> represent,
      Function<byte[], PMTNode> read) {
    var newRemainder = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
    var nextHash = this.getValue();
    var nextNode = read.apply(nextHash);
    // INFO: for NONE, the NEW will be empty
    nextNode.insertNode(newRemainder, val, acc, represent, read);
    var subTip = acc.getTip();
    // INFO: for NEW or NONE, the commonPrefix can't be null as it existed here
    var newExt = new PMTExt(commonPath.getCommonPrefix(), represent.apply(subTip));
    acc.setTip(newExt);
    acc.add(newExt);
    acc.mark(this);
    acc.remove(this);
  }

  // This method is expected to mutate PMTAcc.
  private void handleOnlyExistingRemainderIsLeft(
      byte[] val, PMTAcc acc, PMTPath commonPath, Function<PMTNode, byte[]> represent) {
    var remainder = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
    byte[] sliceVal;
    if (remainder.isNibble()) {
      sliceVal = this.getValue();
    } else {
      var newShorter = new PMTExt(remainder.getTailNibbles(), this.getValue());
      acc.add(newShorter);
      sliceVal = represent.apply(newShorter);
    }
    var newBranch =
        new PMTBranch(val, new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), sliceVal));
    computeAndSetTip(commonPath, newBranch, acc, represent);
    acc.add(newBranch);
    acc.mark(this);
    acc.remove(this);
  }

  // This method is expected to mutate PMTAcc.
  public void getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    switch (commonPath.whichRemainderIsLeft()) {
      case NEW, NONE:
        acc.mark(this);
        var nextHash = this.getValue();
        var nextNode = read.apply(nextHash);
        nextNode.getValue(commonPath.getRemainder(PMTPath.RemainingSubtree.NEW), acc, read);
        break;
      case EXISTING_AND_NEW, EXISTING:
        acc.mark(this);
        acc.setNotFound();
        break;
      default:
        throw new IllegalStateException(
            String.format(UNEXPECTED_SUBTREE_ERROR_MSG, commonPath.whichRemainderIsLeft()));
    }
  }
}
