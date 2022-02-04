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

import java.util.Arrays;
import java.util.function.Function;

public final class PMTLeaf extends PMTNode {

  public static final int EVEN_PREFIX = 2;
  public static final int ODD_PREFIX = 3;

  public PMTLeaf(PMTKey keyNibbles, byte[] newValue) {
    this.keyNibbles = keyNibbles;
    this.value = newValue;
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
        handleExistingRemainder(val, acc, represent, commonPath);
        break;
      case NEW:
        handleNewRemainder(val, acc, represent, commonPath);
        break;
      case EXISTING_AND_NEW:
        handleBothExistingAndNewRemainder(val, acc, represent, commonPath);
        break;
      case NONE:
        handleNoRemainder(key, val, acc);
        break;
      default:
        throw new IllegalStateException(
            String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
    }
  }

  @Override
  public void removeNode(
      PMTKey key, PMTAcc acc, Function<PMTNode, byte[]> represent, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    switch (commonPath.whichRemainderIsLeft()) {
      case NONE -> {
        acc.remove(this);
        acc.setTip(null);
      }
      case NEW, EXISTING, EXISTING_AND_NEW -> acc.setNotFound();
      default -> throw new IllegalStateException(
          String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
    }
  }

  // This method is expected to mutate PMTAcc.
  private void handleNoRemainder(PMTKey key, byte[] val, PMTAcc acc) {
    if (Arrays.equals(val, this.getValue())) {
      acc.setTip(this);
    } else {
      // INFO: we preserve whole key-end as there are no branches
      var newLeaf = new PMTLeaf(key, val);
      acc.setTip(newLeaf);
      acc.add(newLeaf);
      acc.mark(this);
    }
  }

  // This method is expected to mutate PMTAcc.
  private void handleBothExistingAndNewRemainder(
      byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTPath commonPath) {
    PMTBranch newBranch;
    var remainderNew = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
    var remainderOld = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
    var newLeafNew = new PMTLeaf(remainderNew.getTailNibbles(), val);
    var newLeafOld = new PMTLeaf(remainderOld.getTailNibbles(), this.value);
    newBranch =
        new PMTBranch(
            null,
            new PMTBranch.PMTBranchChild(
                remainderNew.getFirstNibble(), represent.apply(newLeafNew)),
            new PMTBranch.PMTBranchChild(
                remainderOld.getFirstNibble(), represent.apply(newLeafOld)));
    computeAndSetTip(commonPath, newBranch, acc, represent);
    acc.add(newLeafNew, newLeafOld, newBranch);
    acc.mark(this);
  }

  // This method is expected to mutate PMTAcc.
  private void handleNewRemainder(
      byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTPath commonPath) {
    PMTKey remainder;
    PMTBranch newBranch;
    PMTLeaf newLeaf;
    remainder = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
    newLeaf = new PMTLeaf(remainder.getTailNibbles(), val);
    newBranch =
        new PMTBranch(
            this.getValue(),
            new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), represent.apply(newLeaf)));
    computeAndSetTip(commonPath, newBranch, acc, represent);
    acc.add(newLeaf, newBranch);
    acc.mark(this);
  }

  // This method is expected to mutate PMTAcc.
  private void handleExistingRemainder(
      byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTPath commonPath) {
    var remainder = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
    var newLeaf = new PMTLeaf(remainder.getTailNibbles(), this.getValue());
    var newBranch =
        new PMTBranch(
            val,
            new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), represent.apply(newLeaf)));
    computeAndSetTip(commonPath, newBranch, acc, represent);
    acc.add(newLeaf, newBranch);
    acc.mark(this);
  }

  // This method is expected to mutate PMTAcc.
  public void getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
    final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
    switch (commonPath.whichRemainderIsLeft()) {
      case NONE:
        acc.setRetVal(this);
        break;
      case NEW, EXISTING, EXISTING_AND_NEW:
        acc.mark(this);
        acc.setNotFound();
        break;
      default:
        throw new IllegalStateException(
            String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
    }
  }
}
