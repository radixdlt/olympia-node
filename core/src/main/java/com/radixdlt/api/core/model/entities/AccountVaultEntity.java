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

package com.radixdlt.api.core.model.entities;

import static com.radixdlt.atom.SubstateTypeId.STAKE_OWNERSHIP;
import static com.radixdlt.atom.SubstateTypeId.TOKENS;

import com.radixdlt.api.core.model.Entity;
import com.radixdlt.api.core.model.KeyQuery;
import com.radixdlt.api.core.model.Resource;
import com.radixdlt.api.core.model.ResourceQuery;
import com.radixdlt.api.core.model.ResourceUnsignedAmount;
import com.radixdlt.api.core.model.StakeUnitResource;
import com.radixdlt.api.core.model.SubstateWithdrawal;
import com.radixdlt.api.core.model.TokenResource;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import java.nio.ByteBuffer;
import java.util.List;
import org.bouncycastle.util.Arrays;

public record AccountVaultEntity(REAddr accountAddress) implements Entity {
  @Override
  public void deposit(ResourceUnsignedAmount amount, TxBuilder txBuilder, RERulesConfig config) {
    final Particle substate;
    if (amount.resource() instanceof TokenResource tokenResource) {
      var tokenAddress = tokenResource.tokenAddress();
      substate = new TokensInAccount(accountAddress, tokenAddress, amount.amount());
    } else if (amount.resource() instanceof StakeUnitResource stakeUnitResource) {
      substate =
          new StakeOwnership(stakeUnitResource.validatorKey(), accountAddress, amount.amount());
    } else {
      throw new IllegalStateException("Unknown resource type " + amount.resource());
    }
    txBuilder.up(substate);
  }

  @Override
  public SubstateWithdrawal withdraw(Resource resource) {
    if (resource instanceof TokenResource tokenResource) {
      var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
      buf.put(SubstateTypeId.TOKENS.id());
      buf.put((byte) 0);
      buf.put(accountAddress.getBytes());
      SubstateIndex<ResourceInBucket> index =
          SubstateIndex.create(buf.array(), TokensInAccount.class);
      return new SubstateWithdrawal(
          index,
          p ->
              p.bucket().resourceAddr().equals(tokenResource.tokenAddress())
                  && p.bucket().getOwner().equals(accountAddress));
    } else if (resource instanceof StakeUnitResource stakeUnitResource) {
      var validatorKey = stakeUnitResource.validatorKey();
      var buf =
          ByteBuffer.allocate(
              2 + ECPublicKey.COMPRESSED_BYTES + (1 + ECPublicKey.COMPRESSED_BYTES));
      buf.put(SubstateTypeId.STAKE_OWNERSHIP.id());
      buf.put((byte) 0);
      buf.put(validatorKey.getCompressedBytes());
      buf.put(accountAddress.getBytes());
      if (buf.hasRemaining()) {
        // Sanity
        throw new IllegalStateException("Buffer Size sanity check failed.");
      }
      SubstateIndex<ResourceInBucket> index =
          SubstateIndex.create(buf.array(), StakeOwnership.class);
      return new SubstateWithdrawal(
          index,
          p ->
              p.bucket().getOwner().equals(accountAddress)
                  && p.bucket().getValidatorKey().equals(validatorKey));
    } else {
      throw new IllegalStateException("Unknown resource: " + resource);
    }
  }

  @Override
  public List<ResourceQuery> getResourceQueries() {
    var tokenIndex =
        SubstateIndex.<ResourceInBucket>create(
            Arrays.concatenate(new byte[] {TOKENS.id(), 0}, accountAddress.getBytes()),
            TokensInAccount.class);
    // Unfortunately we prefixed Stakeownership in the wrong order so we'll need to do a scan
    var ownershipIndex =
        SubstateIndex.<ResourceInBucket>create(STAKE_OWNERSHIP.id(), StakeOwnership.class);
    return List.of(
        ResourceQuery.from(tokenIndex),
        ResourceQuery.from(ownershipIndex, b -> b.bucket().getOwner().equals(accountAddress)));
  }

  @Override
  public List<KeyQuery> getKeyQueries() {
    return List.of();
  }
}
