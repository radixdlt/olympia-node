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

package com.radixdlt.statecomputer.checkpoint;

import static com.radixdlt.atom.TxAction.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedBytes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Generates a genesis atom */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class GenesisProvider implements Provider<VerifiedTxnsAndProof> {
  private static final Logger logger = LogManager.getLogger();
  private final ImmutableList<TokenIssuance> tokenIssuances;
  private final Set<ECPublicKey> validatorKeys;
  private final Set<StakeTokens> stakeTokens;
  private final Optional<List<TxAction>> additionalActions;
  private final GenesisBuilder genesisBuilder;
  private final long timestamp;

  @Inject
  public GenesisProvider(
      GenesisBuilder genesisBuilder,
      @Genesis long timestamp,
      @Genesis ImmutableList<TokenIssuance> tokenIssuances,
      @Genesis Set<StakeTokens> stakeTokens,
      @Genesis Set<ECPublicKey> validatorKeys,
      @Genesis Optional<List<TxAction>> additionalActions) {
    this.genesisBuilder = genesisBuilder;
    this.timestamp = timestamp;
    this.tokenIssuances = tokenIssuances;
    this.validatorKeys = validatorKeys;
    this.stakeTokens = stakeTokens;
    this.additionalActions = additionalActions;
  }

  @Override
  public VerifiedTxnsAndProof get() {
    // Check that issuances are sufficient for delegations
    final var issuances =
        tokenIssuances.stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));

    var actions = new ArrayList<TxAction>();
    actions.add(
        new CreateMutableToken(
            REAddr.ofNativeToken(), "xrd", "Rads", "Radix Tokens", "", "", null));
    var rri = REAddr.ofNativeToken();
    try {
      for (var e : issuances.entrySet()) {
        var addr = REAddr.ofPubKeyAccount(e.getKey());
        actions.add(new MintToken(rri, addr, e.getValue()));
      }

      validatorKeys.stream()
          .sorted(KeyComparator.instance())
          .forEach(
              k -> {
                actions.add(new RegisterValidator(k));
                actions.add(new UpdateValidatorFee(k, 0));
                actions.add(new UpdateAllowDelegationFlag(k, true));
              });

      stakeTokens.stream()
          .sorted(
              Comparator.<StakeTokens, byte[]>comparing(
                      t -> t.fromAddr().getBytes(), UnsignedBytes.lexicographicalComparator())
                  .thenComparing(
                      t -> t.fromAddr().getBytes(), UnsignedBytes.lexicographicalComparator())
                  .thenComparing(StakeTokens::amount))
          .forEach(actions::add);

      additionalActions.ifPresent(actions::addAll);
      var genesis = genesisBuilder.build("hello", timestamp, actions);

      logger.info("gen_create{tx_id={}}", genesis.getId());

      var proof = genesisBuilder.generateGenesisProof(genesis);
      return VerifiedTxnsAndProof.create(List.of(genesis), proof);
    } catch (TxBuilderException | RadixEngineException e) {
      throw new IllegalStateException(e);
    }
  }
}
