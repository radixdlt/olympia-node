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

package com.radixdlt.stateir;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.DelegatedResourceInBucket;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore.EngineStoreInTransaction;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.util.TriConsumer;

/**
 * This class is responsible for constructing the Olympia state IR (intermediate representation)
 * from the substates stored on ledger.
 */
public final class StateIRConstructor {
  public static final class OlympiaStateIRConstructorException extends RuntimeException {
    public OlympiaStateIRConstructorException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static <T> Comparator<T> compareBytes(Function<T, byte[]> extractor) {
    return (a, b) -> Arrays.compare(extractor.apply(a), extractor.apply(b));
  }

  private final EngineStoreInTransaction<LedgerAndBFTProof> tx;
  private final SubstateDeserialization substateDeserialization;

  public StateIRConstructor(
      EngineStoreInTransaction<LedgerAndBFTProof> tx,
      SubstateDeserialization substateDeserialization) {
    this.tx = Objects.requireNonNull(tx);
    this.substateDeserialization = Objects.requireNonNull(substateDeserialization);
  }

  public OlympiaStateIR prepareOlympiaStateIR() {
    final var validators = prepareValidators();
    final var resources = prepareResources();
    final var accounts = prepareAccounts();
    final var balances = prepareBalances(resources, accounts);
    final var stakes = prepareStakes(validators, accounts);
    return new OlympiaStateIR(validators, resources, accounts, balances, stakes);
  }

  private ImmutableList<OlympiaStateIR.Validator> prepareValidators() {
    final var validatorMetaData =
        collectSubstatesOfType(
            SubstateTypeId.VALIDATOR_META_DATA,
            ValidatorMetaData.class,
            Collectors.toMap(ValidatorMetaData::validatorKey, s -> s));

    final var allowDelegationFlags =
        collectSubstatesOfType(
            SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG,
            AllowDelegationFlag.class,
            Collectors.toMap(AllowDelegationFlag::validatorKey, s -> s));

    final var validatorStakeData =
        collectSubstatesOfType(
            SubstateTypeId.VALIDATOR_STAKE_DATA,
            ValidatorStakeData.class,
            Collectors.toMap(ValidatorStakeData::validatorKey, s -> s));

    final var validatorsKeys =
        ImmutableSet.<ECPublicKey>builder()
            .addAll(validatorMetaData.keySet())
            .addAll(allowDelegationFlags.keySet())
            .addAll(validatorStakeData.keySet())
            .build();

    return validatorsKeys.stream()
        .map(
            validatorKey -> {
              final var metadata =
                  validatorMetaData.getOrDefault(
                      validatorKey, ValidatorMetaData.createVirtual(validatorKey));
              final var allowDelegationFlag =
                  allowDelegationFlags.getOrDefault(
                      validatorKey, AllowDelegationFlag.createVirtual(validatorKey));
              final var stakeData =
                  validatorStakeData.getOrDefault(
                      validatorKey, ValidatorStakeData.createVirtual(validatorKey));
              return new OlympiaStateIR.Validator(
                  validatorKey,
                  metadata.name(),
                  metadata.url(),
                  allowDelegationFlag.allowsDelegation(),
                  stakeData.isRegistered(),
                  stakeData.totalStake(),
                  stakeData.totalOwnership(),
                  stakeData.rakePercentage(),
                  stakeData.ownerAddr());
            })
        .sorted(compareBytes(v -> v.validatorKey().getCompressedBytes()))
        .collect(ImmutableList.toImmutableList());
  }

  private ImmutableList<OlympiaStateIR.Resource> prepareResources() {
    final var tokenResources =
        collectSubstatesOfType(
            SubstateTypeId.TOKEN_RESOURCE,
            TokenResource.class,
            Collectors.toMap(TokenResource::addr, s -> s));

    final var tokenResourceMetadata =
        collectSubstatesOfType(
            SubstateTypeId.TOKEN_RESOURCE_METADATA,
            TokenResourceMetadata.class,
            Collectors.toMap(TokenResourceMetadata::addr, s -> s));

    final var resources =
        tokenResources.values().stream()
            .map(
                e -> {
                  final var metadata = tokenResourceMetadata.remove(e.addr());
                  return new OlympiaStateIR.Resource(
                      e.addr(),
                      e.granularity(),
                      e.isMutable(),
                      Optional.ofNullable(e.owner()),
                      metadata.symbol(),
                      metadata.name(),
                      metadata.description(),
                      metadata.iconUrl(),
                      metadata.url());
                })
            .sorted(compareBytes(v -> v.addr().getBytes()))
            .collect(ImmutableList.toImmutableList());

    if (!tokenResourceMetadata.isEmpty()) {
      throw new OlympiaStateIRConstructorException(
          "Not all TokenResourceMetadata have a corresponding TokenResource substate!", null);
    }

    return resources;
  }

  private ImmutableList<OlympiaStateIR.Account> prepareAccounts() {
    final var accountsWithTokens =
        collectSubstatesOfType(
            SubstateTypeId.TOKENS,
            TokensInAccount.class,
            Collectors.mapping(
                substate -> new OlympiaStateIR.Account(substate.holdingAddress()),
                Collectors.toSet()));

    final var accountsWithPreparedStake =
        extractAccountsFromStakes(SubstateTypeId.PREPARED_STAKE, PreparedStake.class);
    final var accountsWithStakeOwnership =
        extractAccountsFromStakes(SubstateTypeId.STAKE_OWNERSHIP, StakeOwnership.class);
    final var accountsWithPreparedUnstakeOwnership =
        extractAccountsFromStakes(SubstateTypeId.PREPARED_UNSTAKE, PreparedUnstakeOwnership.class);
    final var accountsWithExitingStake =
        extractAccountsFromStakes(SubstateTypeId.EXITING_STAKE, ExitingStake.class);

    return Stream.of(
            accountsWithTokens.stream(),
            accountsWithPreparedStake.stream(),
            accountsWithStakeOwnership.stream(),
            accountsWithPreparedUnstakeOwnership.stream(),
            accountsWithExitingStake.stream())
        .flatMap(s -> s)
        .distinct()
        .sorted(compareBytes(v -> v.addr().getBytes()))
        .collect(ImmutableList.toImmutableList());
  }

  private <T extends DelegatedResourceInBucket>
      ImmutableSet<OlympiaStateIR.Account> extractAccountsFromStakes(
          SubstateTypeId substateTypeId, Class<T> substateClazz) {
    return collectSubstatesOfType(
        substateTypeId,
        substateClazz,
        Collectors.mapping(
            substate -> new OlympiaStateIR.Account(substate.owner()),
            ImmutableSet.toImmutableSet()));
  }

  private ImmutableList<OlympiaStateIR.AccountBalance> prepareBalances(
      ImmutableList<OlympiaStateIR.Resource> resources,
      ImmutableList<OlympiaStateIR.Account> accounts) {
    final var resourceIdxMap =
        IntStream.range(0, resources.size())
            .boxed()
            .collect(Collectors.toMap(i -> resources.get(i).addr(), i -> i));

    final var accountIdxMap =
        IntStream.range(0, accounts.size())
            .boxed()
            .collect(Collectors.toMap(i -> accounts.get(i).addr(), i -> i));

    final Map<REAddr, Map<REAddr, UInt256>> tokensByAccountAndResource = new HashMap<>();

    final TriConsumer<REAddr, REAddr, UInt256> accumulateTokensToMap =
        (holdingAddr, resourceAddr, amount) -> {
          final var accountResources =
              tokensByAccountAndResource.computeIfAbsent(holdingAddr, unused -> new HashMap<>());
          final var currBalance = accountResources.getOrDefault(resourceAddr, UInt256.ZERO);
          final var newBalance = currBalance.add(amount);
          accountResources.put(resourceAddr, newBalance);
        };

    processSubstatesOfType(
        SubstateTypeId.TOKENS,
        TokensInAccount.class,
        s -> accumulateTokensToMap.accept(s.holdingAddress(), s.resourceAddr(), s.amount()));

    // Ignoring unstake delay for now
    processSubstatesOfType(
        SubstateTypeId.PREPARED_UNSTAKE,
        PreparedUnstakeOwnership.class,
        s -> accumulateTokensToMap.accept(s.owner(), REAddr.ofNativeToken(), s.amount()));

    processSubstatesOfType(
        SubstateTypeId.EXITING_STAKE,
        ExitingStake.class,
        s -> accumulateTokensToMap.accept(s.owner(), REAddr.ofNativeToken(), s.amount()));

    return tokensByAccountAndResource.entrySet().stream()
        .flatMap(
            accountEntry -> {
              final var accountIdx = accountIdxMap.get(accountEntry.getKey());
              return accountEntry.getValue().entrySet().stream()
                  .map(
                      resourceEntry -> {
                        final var resourceIdx = resourceIdxMap.get(resourceEntry.getKey());
                        return new OlympiaStateIR.AccountBalance(
                            accountIdx, resourceIdx, resourceEntry.getValue());
                      });
            })
        .sorted(
            Comparator.comparingInt(OlympiaStateIR.AccountBalance::accountIndex)
                .thenComparing(OlympiaStateIR.AccountBalance::resourceIndex)
                .thenComparing(OlympiaStateIR.AccountBalance::amount))
        .collect(ImmutableList.toImmutableList());
  }

  private ImmutableList<OlympiaStateIR.Stake> prepareStakes(
      ImmutableList<OlympiaStateIR.Validator> validators,
      ImmutableList<OlympiaStateIR.Account> accounts) {
    final var validatorIdxMap =
        IntStream.range(0, validators.size())
            .boxed()
            .collect(Collectors.toMap(i -> validators.get(i).validatorKey(), i -> i));

    final var accountIdxMap =
        IntStream.range(0, accounts.size())
            .boxed()
            .collect(Collectors.toMap(i -> accounts.get(i).addr(), i -> i));

    final Map<REAddr, Map<ECPublicKey, UInt256>> stakeByAccountAndValidator = new HashMap<>();

    final Consumer<DelegatedResourceInBucket> accumulateStakeToMap =
        substate -> {
          final var accountStakes =
              stakeByAccountAndValidator.computeIfAbsent(
                  substate.owner(), unused -> new HashMap<>());
          final var currStake = accountStakes.getOrDefault(substate.delegateKey(), UInt256.ZERO);
          final var newStake = currStake.add(substate.amount());
          accountStakes.put(substate.delegateKey(), newStake);
        };

    processSubstatesOfType(
        SubstateTypeId.PREPARED_STAKE, PreparedStake.class, accumulateStakeToMap::accept);
    processSubstatesOfType(
        SubstateTypeId.STAKE_OWNERSHIP, StakeOwnership.class, accumulateStakeToMap::accept);

    return stakeByAccountAndValidator.entrySet().stream()
        .flatMap(
            accountEntry -> {
              final var accountIdx = accountIdxMap.get(accountEntry.getKey());
              return accountEntry.getValue().entrySet().stream()
                  .map(
                      stakeEntry -> {
                        final var validatorIdx = validatorIdxMap.get(stakeEntry.getKey());
                        return new OlympiaStateIR.Stake(
                            accountIdx, validatorIdx, stakeEntry.getValue());
                      });
            })
        .sorted(
            Comparator.comparingInt(OlympiaStateIR.Stake::accountIndex)
                .thenComparing(OlympiaStateIR.Stake::validatorIndex)
                .thenComparing(OlympiaStateIR.Stake::amount))
        .collect(ImmutableList.toImmutableList());
  }

  private <T extends Particle, A, R> R collectSubstatesOfType(
      SubstateTypeId substateTypeId, Class<T> substateClazz, Collector<? super T, A, R> collector) {
    final var accum = collector.supplier().get();
    processSubstatesOfType(
        substateTypeId, substateClazz, substate -> collector.accumulator().accept(accum, substate));
    return collector.finisher().apply(accum);
  }

  private <T extends Particle> void processSubstatesOfType(
      SubstateTypeId substateTypeId, Class<T> substateClazz, Consumer<T> consumer) {
    try (var cursor =
        tx.openIndexedCursor(SubstateIndex.create(substateTypeId.id(), substateClazz))) {
      cursor.forEachRemaining(rawSubstate -> consumer.accept(parseRawSubstateBytes(rawSubstate)));
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Particle> T parseRawSubstateBytes(RawSubstateBytes rawSubstateBytes) {
    try {
      return (T) substateDeserialization.deserialize(rawSubstateBytes.getData());
    } catch (DeserializeException e) {
      throw new OlympiaStateIRConstructorException("Failed to deserialize substate", e);
    }
  }
}
