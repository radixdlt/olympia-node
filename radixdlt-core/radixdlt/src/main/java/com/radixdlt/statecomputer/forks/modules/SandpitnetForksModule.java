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

package com.radixdlt.statecomputer.forks.modules;

import static com.radixdlt.constraintmachine.REInstruction.REMicroOp.MSG;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.validators.state.*;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.ForkBuilder;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

public final class SandpitnetForksModule extends AbstractModule {
  private static final Set<String> RESERVED_SYMBOLS =
      Set.of("xrd", "xrds", "exrd", "exrds", "rad", "rads", "rdx", "rdxs", "radix");

  @ProvidesIntoSet
  ForkBuilder testnetGenesis() {
    return new ForkBuilder(
        "testnet-genesis",
        0L,
        RERulesVersion.OLYMPIA_V1,
        new RERulesConfig(
            RESERVED_SYMBOLS,
            Pattern.compile("[a-z0-9]+"),
            FeeTable.create(
                Amount.ofMicroTokens(200), // 0.0002XRD per byte fee
                Map.of(
                    TokenResource.class, Amount.ofTokens(100), // 100XRD per resource
                    ValidatorRegisteredCopy.class, Amount.ofTokens(5), // 5XRD per validator update
                    ValidatorFeeCopy.class, Amount.ofTokens(5), // 5XRD per register update
                    ValidatorOwnerCopy.class, Amount.ofTokens(5), // 5XRD per register update
                    ValidatorMetaData.class, Amount.ofTokens(5), // 5XRD per register update
                    AllowDelegationFlag.class, Amount.ofTokens(5), // 5XRD per register update
                    PreparedStake.class, Amount.ofMilliTokens(500), // 0.5XRD per stake
                    PreparedUnstakeOwnership.class, Amount.ofMilliTokens(500) // 0.5XRD per unstake
                    )),
            (long) 1024 * 1024, // 1MB max user transaction size
            OptionalInt.of(50), // 50 Txns per round
            10_000, // Rounds per epoch
            500, // Two weeks worth of epochs
            Amount.ofTokens(90), // Minimum stake
            500, // Two weeks worth of epochs
            Amount.ofMicroTokens(2307700), // Rewards per proposal
            9800, // 98.00% threshold for completed proposals to get any rewards,
            100, // 100 max validators
            MSG.maxLength()));
  }

  @ProvidesIntoSet
  ForkBuilder shutdown() {
    return new ForkBuilder(
            "shutdown",
            ImmutableSet.of(new CandidateForkConfig.Threshold((short) 8000, 1)),
            1L,
            Long.MAX_VALUE,
            RERulesVersion.OLYMPIA_V1,
            new RERulesConfig(
                RESERVED_SYMBOLS,
                Pattern.compile("[a-z0-9]+"),
                FeeTable.create(
                    Amount.ofMicroTokens(Long.MAX_VALUE), Map.of()), // Highest possible fee
                0L, // No txns allowed
                OptionalInt.of(0),
                Long.MAX_VALUE, // No more epochs
                1,
                Amount.ofTokens(90), // Same as prev fork
                Long.MAX_VALUE, // No more unstaking
                Amount.ofMicroTokens(0), // No more rewards
                0, // Unused
                100,
                MSG.maxLength()))
        .withShutdown();
  }
}
