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

package com.radixdlt.api.core.model;

import com.google.common.base.Throwables;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.api.core.model.entities.AccountVaultEntity;
import com.radixdlt.api.core.model.entities.EntityDoesNotSupportDataObjectException;
import com.radixdlt.api.core.model.entities.EntityDoesNotSupportResourceDepositException;
import com.radixdlt.api.core.model.entities.EntityDoesNotSupportResourceWithdrawException;
import com.radixdlt.api.core.model.entities.ExitingStakeVaultEntity;
import com.radixdlt.api.core.model.entities.InvalidDataObjectException;
import com.radixdlt.api.core.model.entities.PreparedStakeVaultEntity;
import com.radixdlt.api.core.model.entities.PreparedUnstakeVaultEntity;
import com.radixdlt.api.core.model.entities.SystemEntity;
import com.radixdlt.api.core.model.entities.TokenEntity;
import com.radixdlt.api.core.model.entities.ValidatorEntity;
import com.radixdlt.api.core.model.entities.ValidatorSystemEntity;
import com.radixdlt.api.core.openapitools.model.*;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.UnclaimedREAddr;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.construction.DelegateStakePermissionException;
import com.radixdlt.application.tokens.construction.MinimumStakeException;
import com.radixdlt.application.tokens.state.ResourceData;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.validators.construction.InvalidRakeIncreaseException;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.application.validators.state.ValidatorUpdatingData;
import com.radixdlt.atom.MessageTooLongException;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.exceptions.SubstateNotFoundException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.engine.FeeConstructionException;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class CoreModelMapper {
  private static final String TOKEN_TYPE = "Token";
  private static final String STAKE_UNIT_TYPE = "StakeUnit";
  private static final String SYSTEM_ADDRESS = "system";
  private static final String PREPARED_STAKES_ADDRESS = "prepared_stakes";
  private static final String PREPARED_UNSTAKES_ADDRESS = "prepared_unstakes";
  private static final String EXITING_UNSTAKES_ADDRESS = "exiting_unstakes";
  private static final ECPublicKey MOCK_PUBLIC_KEY = PrivateKeys.ofNumeric(1).getPublicKey();
  private final Addressing addressing;
  private final Network network;
  private final CurrentForkView currentForkView;

  @Inject
  CoreModelMapper(
      @NetworkId int networkId, Addressing addressing, CurrentForkView currentForkView) {
    this.network = Network.ofId(networkId).orElseThrow();
    this.addressing = addressing;
    this.currentForkView = currentForkView;
  }

  public void verifyNetwork(NetworkIdentifier networkIdentifier) throws CoreApiException {
    if (!networkIdentifier.getNetwork().equals(this.network.name().toLowerCase())) {
      throw CoreApiException.notSupported(
          new NetworkNotSupportedError()
              .addSupportedNetworksItem(
                  new NetworkIdentifier().network(this.network.name().toLowerCase()))
              .type(NetworkNotSupportedError.class.getSimpleName()));
    }
  }

  public Pair<ECPublicKey, ECDSASignature> keyAndSignature(Signature signature)
      throws CoreApiException {
    var bytes = bytes(signature.getBytes());
    ECDSASignature sig;
    try {
      sig = ECDSASignature.decodeFromDER(bytes);
    } catch (IllegalArgumentException e) {
      throw CoreApiException.badRequest(
          new InvalidSignatureError()
              .invalidSignature(signature.getBytes())
              .type(InvalidSignatureError.class.getSimpleName()));
    }
    var publicKey = ecPublicKey(signature.getPublicKey());
    return Pair.of(publicKey, sig);
  }

  public ECPublicKey ecPublicKey(PublicKey publicKey) throws CoreApiException {
    var bytes = bytes(publicKey.getHex());
    try {
      return ECPublicKey.fromBytes(bytes);
    } catch (PublicKeyException e) {
      throw CoreApiException.badRequest(
          new InvalidPublicKeyError()
              .invalidPublicKey(publicKey)
              .type(InvalidPublicKeyError.class.getSimpleName()));
    }
  }

  public byte[] bytes(String hex) throws CoreApiException {
    try {
      return Bytes.fromHexString(hex);
    } catch (IllegalArgumentException e) {
      throw CoreApiException.badRequest(
          new InvalidHexError().invalidHex(hex).type(InvalidHexError.class.getSimpleName()));
    }
  }

  public CoreError builderErrorDetails(TxBuilderException e) {
    if (e instanceof MinimumStakeException minimumStakeException) {
      return new BelowMinimumStakeError()
          .minimumStake(nativeTokenAmount(minimumStakeException.getMinimumStake()))
          .attemptedToStake(nativeTokenAmount(minimumStakeException.getAttempt()))
          .type(BelowMinimumStakeError.class.getSimpleName());
    } else if (e instanceof DelegateStakePermissionException delegateException) {
      return new NotValidatorOwnerError()
          .owner(entityIdentifier(delegateException.getOwner()))
          .user(entityIdentifier(delegateException.getUser()))
          .type(NotValidatorOwnerError.class.getSimpleName());
    } else if (e instanceof InvalidDataObjectException invalidDataObjectException) {
      return new InvalidDataObjectError()
          .invalidDataObject(invalidDataObjectException.getDataObject().dataObject())
          .message(invalidDataObjectException.getMessage())
          .type(InvalidDataObjectError.class.getSimpleName());
    } else if (e instanceof InvalidRakeIncreaseException rakeIncreaseException) {
      return new AboveMaximumValidatorFeeIncreaseError()
          .maximumValidatorFeeIncrease(rakeIncreaseException.getMaxRakeIncrease())
          .attemptedValidatorFeeIncrease(rakeIncreaseException.getIncreaseAttempt())
          .type(AboveMaximumValidatorFeeIncreaseError.class.getSimpleName());
    } else if (e instanceof EntityDoesNotSupportDataObjectException dataObjectException) {
      return new DataObjectNotSupportedByEntityError()
          .dataObjectNotSupported(dataObjectException.getDataObject().dataObject())
          .entityIdentifier(entityIdentifier(dataObjectException.getEntity()))
          .type(DataObjectNotSupportedByEntityError.class.getSimpleName());
    } else if (e instanceof EntityDoesNotSupportResourceDepositException depositException) {
      return new ResourceDepositOperationNotSupportedByEntityError()
          .resourceDepositNotSupported(resourceIdentifier(depositException.getResource()))
          .entityIdentifier(entityIdentifier(depositException.getEntity()))
          .type(ResourceDepositOperationNotSupportedByEntityError.class.getSimpleName());
    } else if (e instanceof EntityDoesNotSupportResourceWithdrawException withdrawException) {
      return new ResourceWithdrawOperationNotSupportedByEntityError()
          .resourceWithdrawNotSupported(resourceIdentifier(withdrawException.getResource()))
          .entityIdentifier(entityIdentifier(withdrawException.getEntity()))
          .type(ResourceWithdrawOperationNotSupportedByEntityError.class.getSimpleName());
    } else if (e instanceof MessageTooLongException messageTooLongException) {
      return new MessageTooLongError()
          .maximumMessageLength(messageTooLongException.getLimit())
          .attemptedMessageLength(messageTooLongException.getAttemptedLength())
          .type(MessageTooLongError.class.getSimpleName());
    } else if (e instanceof FeeConstructionException feeConstructionException) {
      return new FeeConstructionError()
          .attempts(feeConstructionException.getAttempts())
          .type(FeeConstructionError.class.getSimpleName());
    } else if (e instanceof NotEnoughResourcesException notEnoughResourcesException) {
      var resourceIdentifier = resourceIdentifier(notEnoughResourcesException.getResource());
      return new NotEnoughResourcesError()
          .attemptedToTake(
              new ResourceAmount()
                  .resourceIdentifier(resourceIdentifier)
                  .value(notEnoughResourcesException.getRequested().toString()))
          .available(
              new ResourceAmount()
                  .resourceIdentifier(resourceIdentifier)
                  .value(notEnoughResourcesException.getAvailable().toString()))
          .fee(nativeTokenAmount(notEnoughResourcesException.getFee()))
          .type(NotEnoughResourcesError.class.getSimpleName());
    } else if (e
        instanceof NotEnoughNativeTokensForFeesException notEnoughNativeTokensForFeesException) {
      return new NotEnoughNativeTokensForFeesError()
          .available(nativeTokenAmount(notEnoughNativeTokensForFeesException.getAvailable()))
          .feeEstimate(nativeTokenAmount(notEnoughNativeTokensForFeesException.getFee()))
          .type(NotEnoughNativeTokensForFeesError.class.getSimpleName());
    }

    throw new IllegalStateException(e);
  }

  public AccountVaultEntity feePayerEntity(EntityIdentifier entityIdentifier)
      throws CoreApiException {
    var feePayer = entity(entityIdentifier);
    if (!(feePayer instanceof AccountVaultEntity accountVaultEntity)) {
      throw CoreApiException.badRequest(
          new InvalidFeePayerEntityError()
              .invalidFeePayerEntity(entityIdentifier)
              .type(InvalidFeePayerEntityError.class.getSimpleName()));
    }
    return accountVaultEntity;
  }

  private static CoreApiException invalidAddress(String address) {
    return CoreApiException.badRequest(
        new InvalidAddressError()
            .invalidAddress(address)
            .type(InvalidAddressError.class.getSimpleName()));
  }

  private static CoreApiException invalidSubEntity(SubEntity subEntity) {
    return CoreApiException.badRequest(
        new InvalidSubEntityError()
            .invalidSubEntity(subEntity)
            .type(InvalidSubEntityError.class.getSimpleName()));
  }

  private Entity validatorAddressEntity(EntityIdentifier entityIdentifier) throws CoreApiException {
    var address = entityIdentifier.getAddress();
    var key = addressing.forValidators().parseOrThrow(address, s -> invalidAddress(address));
    var subEntity = entityIdentifier.getSubEntity();
    if (subEntity == null) {
      return new ValidatorEntity(key);
    }

    var metadata = subEntity.getMetadata();
    if (metadata != null) {
      throw invalidSubEntity(subEntity);
    }

    var subEntityAddress = subEntity.getAddress();
    if (!subEntityAddress.equals(SYSTEM_ADDRESS)) {
      throw invalidSubEntity(subEntity);
    }

    return new ValidatorSystemEntity(key);
  }

  private Entity accountAddressEntity(EntityIdentifier entityIdentifier) throws CoreApiException {
    var address = entityIdentifier.getAddress();
    var accountAddress =
        addressing.forAccounts().parseOrThrow(address, s -> invalidAddress(address));
    var subEntity = entityIdentifier.getSubEntity();
    if (subEntity == null) {
      return new AccountVaultEntity(accountAddress);
    }

    switch (subEntity.getAddress()) {
      case PREPARED_STAKES_ADDRESS -> {
        var metadata = subEntity.getMetadata();
        if (metadata == null
            || metadata.getEpochUnlock() != null
            || metadata.getValidatorAddress() == null) {
          throw invalidSubEntity(subEntity);
        }
        var validator =
            addressing
                .forValidators()
                .parseOrThrow(metadata.getValidatorAddress(), s -> invalidAddress(address));
        return new PreparedStakeVaultEntity(accountAddress, validator);
      }
      case PREPARED_UNSTAKES_ADDRESS -> {
        var metadata = subEntity.getMetadata();
        if (metadata != null) {
          throw invalidSubEntity(subEntity);
        }
        return new PreparedUnstakeVaultEntity(accountAddress);
      }
      case EXITING_UNSTAKES_ADDRESS -> {
        var metadata = subEntity.getMetadata();
        if (metadata == null
            || metadata.getEpochUnlock() == null
            || metadata.getValidatorAddress() == null) {
          throw invalidSubEntity(subEntity);
        }

        var validator =
            addressing
                .forValidators()
                .parseOrThrow(metadata.getValidatorAddress(), s -> invalidAddress(address));
        return new ExitingStakeVaultEntity(accountAddress, validator, metadata.getEpochUnlock());
      }
      default -> throw invalidSubEntity(subEntity);
    }
  }

  private Entity resourceAddressEntity(EntityIdentifier entityIdentifier) throws CoreApiException {
    var address = entityIdentifier.getAddress();
    var pair = addressing.forResources().parseOrThrow(address, s -> invalidAddress(address));
    return new TokenEntity(pair.getFirst(), pair.getSecond());
  }

  private Entity systemAddressEntity(EntityIdentifier entityIdentifier) throws CoreApiException {
    var subEntity = entityIdentifier.getSubEntity();
    if (subEntity != null) {
      throw invalidSubEntity(subEntity);
    }

    return SystemEntity.instance();
  }

  public Entity entity(EntityIdentifier entityIdentifier) throws CoreApiException {
    var address = entityIdentifier.getAddress();
    if (address.equals(SYSTEM_ADDRESS)) {
      return systemAddressEntity(entityIdentifier);
    }

    // TODO: Combine addressing schemes to remove switch/case statement
    var addressType = addressing.getAddressType(address).orElseThrow(() -> invalidAddress(address));
    return switch (addressType) {
      case VALIDATOR -> validatorAddressEntity(entityIdentifier);
      case ACCOUNT -> accountAddressEntity(entityIdentifier);
      case RESOURCE -> resourceAddressEntity(entityIdentifier);
      default -> throw new IllegalStateException("Unknown addressType: " + addressType);
    };
  }

  public Optional<AccountVaultEntity> accountVaultEntity(EntityIdentifier entityIdentifier)
      throws CoreApiException {
    var entity = entity(entityIdentifier);
    if (!(entity instanceof AccountVaultEntity accountVaultEntity)) {
      return Optional.empty();
    }
    return Optional.of(accountVaultEntity);
  }

  public DataOperation dataOperation(Data data) throws CoreApiException {
    if (data == null) {
      return null;
    }

    final ClassToInstanceMap<Object> parsed;
    var dataObject = data.getDataObject();
    if (dataObject instanceof PreparedValidatorOwner preparedValidatorOwner) {
      var owner = preparedValidatorOwner.getOwner();
      var accountVaultEntity =
          accountVaultEntity(owner)
              .orElseThrow(
                  () ->
                      CoreApiException.badRequest(
                          new InvalidDataObjectError().invalidDataObject(preparedValidatorOwner)));
      var ownerAddress = accountVaultEntity.accountAddress();
      parsed = ImmutableClassToInstanceMap.of(REAddr.class, ownerAddress);
    } else if (dataObject instanceof TokenData tokenData && tokenData.getOwner() != null) {
      var owner = tokenData.getOwner();
      var accountVaultEntity =
          accountVaultEntity(owner)
              .orElseThrow(
                  () ->
                      CoreApiException.badRequest(
                          new InvalidDataObjectError().invalidDataObject(tokenData)));
      var key =
          accountVaultEntity
              .accountAddress()
              .publicKey()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Account vault should only have account addresses"));
      parsed = ImmutableClassToInstanceMap.of(ECPublicKey.class, key);
    } else {
      parsed = ImmutableClassToInstanceMap.of();
    }

    return new DataOperation(data, parsed);
  }

  public OperationTxBuilder operationTxBuilder(String message, List<OperationGroup> operationGroups)
      throws CoreApiException {
    var entityOperationGroups = new ArrayList<List<EntityOperation>>();
    for (var group : operationGroups) {
      var entityOperationGroup = new ArrayList<EntityOperation>();
      for (var op : group.getOperations()) {
        var entityOperation =
            EntityOperation.from(
                entity(op.getEntityIdentifier()),
                resourceOperation(op.getAmount()),
                dataOperation(op.getData()));
        entityOperationGroup.add(entityOperation);
      }
      entityOperationGroups.add(entityOperationGroup);
    }

    return new OperationTxBuilder(message, entityOperationGroups, currentForkView);
  }

  public Txn txn(String hex) throws CoreApiException {
    var bytes = bytes(hex);
    return Txn.create(bytes);
  }

  public AID txnId(TransactionIdentifier transactionIdentifier) throws CoreApiException {
    var hash = transactionIdentifier.getHash();
    try {
      return AID.from(hash);
    } catch (IllegalArgumentException e) {
      throw CoreApiException.badRequest(
          new InvalidTransactionHashError()
              .invalidTransactionHash(hash)
              .type(InvalidTransactionHashError.class.getSimpleName()));
    }
  }

  public long limit(Long limit) {
    if (limit == null) {
      return 0L;
    }

    if (limit < 0) {
      throw new IllegalStateException("Limit must be >= 0");
    }

    return limit;
  }

  public CoreError notFoundErrorDetails(PartialStateIdentifier partialStateIdentifier) {
    return new StateIdentifierNotFoundError()
        .stateIdentifier(partialStateIdentifier)
        .type(StateIdentifierNotFoundError.class.getSimpleName());
  }

  public Pair<Long, HashCode> partialStateIdentifier(PartialStateIdentifier partialStateIdentifier)
      throws CoreApiException {
    if (partialStateIdentifier == null) {
      return Pair.of(0L, null);
    }

    if (partialStateIdentifier.getStateVersion() < 0L) {
      throw CoreApiException.badRequest(
          new InvalidPartialStateIdentifierError()
              .invalidPartialStateIdentifier(partialStateIdentifier)
              .type(InvalidPartialStateIdentifierError.class.getSimpleName()));
    }

    final HashCode accumulator;
    if (partialStateIdentifier.getTransactionAccumulator() != null) {
      var bytes = bytes(partialStateIdentifier.getTransactionAccumulator());
      accumulator = HashCode.fromBytes(bytes);
    } else {
      accumulator = null;
    }

    return Pair.of(partialStateIdentifier.getStateVersion(), accumulator);
  }

  public ResourceOperation resourceOperation(ResourceAmount resourceAmount)
      throws CoreApiException {
    if (resourceAmount == null) {
      return null;
    }

    var bigInteger = new BigInteger(resourceAmount.getValue());
    var isPositive = bigInteger.compareTo(BigInteger.ZERO) > 0;

    return ResourceOperation.from(
        resource(resourceAmount.getResourceIdentifier()),
        UInt256.from((isPositive ? bigInteger : bigInteger.negate()).toByteArray()),
        isPositive);
  }

  public Resource resource(ResourceIdentifier resourceIdentifier) throws CoreApiException {
    if (resourceIdentifier instanceof TokenResourceIdentifier tokenResourceIdentifier) {
      var rri = tokenResourceIdentifier.getRri();
      var symbolAndAddr = addressing.forResources().parseOrThrow(rri, s -> invalidAddress(rri));
      return new com.radixdlt.api.core.model.TokenResource(
          symbolAndAddr.getFirst(), symbolAndAddr.getSecond());
    } else if (resourceIdentifier
        instanceof StakeUnitResourceIdentifier stakeUnitResourceIdentifier) {
      var validatorAddress = stakeUnitResourceIdentifier.getValidatorAddress();
      var key =
          addressing
              .forValidators()
              .parseOrThrow(validatorAddress, s -> invalidAddress(validatorAddress));
      return new StakeUnitResource(key);
    } else {
      throw new IllegalStateException("Unknown resourceIdentifier: " + resourceIdentifier);
    }
  }

  public Peer peer(ECPublicKey key) {
    return new Peer().peerId(addressing.forNodes().of(key));
  }

  public Peer peer(PeersView.PeerInfo peerInfo) {
    return new Peer().peerId(addressing.forNodes().of(peerInfo.getNodeId().getPublicKey()));
  }

  public ResourceIdentifier resourceIdentifier(Resource resource) {
    if (resource instanceof com.radixdlt.api.core.model.TokenResource tokenResource) {
      return new TokenResourceIdentifier()
          .rri(addressing.forResources().of(tokenResource.symbol(), tokenResource.tokenAddress()))
          .type(TOKEN_TYPE);
    } else if (resource instanceof StakeUnitResource stakeUnitResource) {
      return new StakeUnitResourceIdentifier()
          .validatorAddress(addressing.forValidators().of(stakeUnitResource.validatorKey()))
          .type(STAKE_UNIT_TYPE);
    } else {
      throw new IllegalStateException("Unknown resource " + resource);
    }
  }

  public EntityIdentifier entityIdentifier(Entity entity) {
    if (entity instanceof AccountVaultEntity account) {
      return entityIdentifier(account.accountAddress());
    } else if (entity instanceof PreparedStakeVaultEntity stake) {
      return entityIdentifier(stake.accountAddress())
          .subEntity(
              new SubEntity()
                  .address(PREPARED_STAKES_ADDRESS)
                  .metadata(
                      new SubEntityMetadata()
                          .validatorAddress(addressing.forValidators().of(stake.validatorKey()))));
    } else if (entity instanceof PreparedUnstakeVaultEntity unstake) {
      return entityIdentifier(unstake.accountAddress())
          .subEntity(new SubEntity().address(PREPARED_UNSTAKES_ADDRESS));
    } else if (entity instanceof ValidatorEntity validator) {
      return entityIdentifier(validator.validatorKey());
    } else if (entity instanceof ValidatorSystemEntity validatorSystem) {
      return entityIdentifier(validatorSystem.validatorKey())
          .subEntity(new SubEntity().address(SYSTEM_ADDRESS));
    } else if (entity instanceof TokenEntity tokenEntity) {
      return entityIdentifier(tokenEntity.tokenAddr(), tokenEntity.symbol());
    } else if (entity instanceof ExitingStakeVaultEntity exiting) {
      return entityIdentifier(
          exiting.accountAddress(), exiting.validatorKey(), exiting.epochUnlock());
    } else if (entity instanceof SystemEntity) {
      return new EntityIdentifier().address(SYSTEM_ADDRESS);
    } else {
      throw new IllegalStateException("Unknown entity: " + entity);
    }
  }

  public EntityIdentifier entityIdentifier(
      REAddr accountAddress, ECPublicKey validatorKey, long epochUnlock) {
    return new EntityIdentifier()
        .address(addressing.forAccounts().of(accountAddress))
        .subEntity(
            new SubEntity()
                .address(EXITING_UNSTAKES_ADDRESS)
                .metadata(
                    new SubEntityMetadata()
                        .validatorAddress(addressing.forValidators().of(validatorKey))
                        .epochUnlock(epochUnlock)));
  }

  public EntityIdentifier entityIdentifier(REAddr tokenAddress, String symbol) {
    return new EntityIdentifier().address(addressing.forResources().of(symbol, tokenAddress));
  }

  public EntityIdentifier entityIdentifierExitingStake(
      REAddr accountAddress, ECPublicKey validatorKey, long epochUnlock) {
    return new EntityIdentifier()
        .address(addressing.forAccounts().of(accountAddress))
        .subEntity(
            new SubEntity()
                .address(EXITING_UNSTAKES_ADDRESS)
                .metadata(
                    new SubEntityMetadata()
                        .validatorAddress(addressing.forValidators().of(validatorKey))
                        .epochUnlock(epochUnlock)));
  }

  public EntityIdentifier entityIdentifierPreparedUnstake(REAddr accountAddress) {
    return new EntityIdentifier()
        .address(addressing.forAccounts().of(accountAddress))
        .subEntity(new SubEntity().address(PREPARED_UNSTAKES_ADDRESS));
  }

  public EntityIdentifier entityIdentifierPreparedStake(
      REAddr accountAddress, ECPublicKey validatorKey) {
    return new EntityIdentifier()
        .address(addressing.forAccounts().of(accountAddress))
        .subEntity(
            new SubEntity()
                .address(PREPARED_STAKES_ADDRESS)
                .metadata(
                    new SubEntityMetadata()
                        .validatorAddress(addressing.forValidators().of(validatorKey))));
  }

  public EntityIdentifier entityIdentifier(REAddr accountAddress) {
    return new EntityIdentifier().address(addressing.forAccounts().of(accountAddress));
  }

  public EntityIdentifier entityIdentifier(ECPublicKey validatorKey) {
    return new EntityIdentifier().address(addressing.forValidators().of(validatorKey));
  }

  public EntityIdentifier entityIdentifierValidatorSystem(ECPublicKey validatorKey) {
    return new EntityIdentifier()
        .address(addressing.forValidators().of(validatorKey))
        .subEntity(new SubEntity().address(SYSTEM_ADDRESS));
  }

  public PublicKey publicKey(ECPublicKey publicKey) {
    return new PublicKey().hex(publicKey.toHex());
  }

  public StateIdentifier stateIdentifier(AccumulatorState accumulatorState) {
    return new StateIdentifier()
        .stateVersion(accumulatorState.getStateVersion())
        .transactionAccumulator(Bytes.toHexString(accumulatorState.getAccumulatorHash().asBytes()));
  }

  public EngineStateIdentifier engineStateIdentifier(LedgerProof ledgerProof) {
    return ledgerProof
        .getNextValidatorSet()
        .map(
            vset ->
                new EngineStateIdentifier()
                    .stateIdentifier(stateIdentifier(ledgerProof.getAccumulatorState()))
                    .epoch(ledgerProof.getEpoch() + 1)
                    .round(0L)
                    .timestamp(ledgerProof.timestamp()))
        .orElseGet(
            () ->
                new EngineStateIdentifier()
                    .stateIdentifier(stateIdentifier(ledgerProof.getAccumulatorState()))
                    .epoch(ledgerProof.getEpoch())
                    .round(ledgerProof.getView().number())
                    .timestamp(ledgerProof.timestamp()));
  }

  public SubstateTypeIdentifier substateTypeIdentifier(Class<? extends Particle> substateClass) {
    var name = SubstateTypeMapping.getName(SubstateTypeId.valueOf(substateClass));
    return new SubstateTypeIdentifier().type(name);
  }

  public TokenResourceIdentifier create(REAddr tokenAddress, String symbol) {
    return (TokenResourceIdentifier)
        new TokenResourceIdentifier()
            .rri(addressing.forResources().of(symbol, tokenAddress))
            .type(TOKEN_TYPE);
  }

  public ResourceAmount nativeTokenAmount(boolean positive, UInt256 value) {
    return new ResourceAmount()
        .resourceIdentifier(nativeToken())
        .value(positive ? value.toString() : "-" + value);
  }

  public ResourceAmount stakeUnitAmount(ECPublicKey validatorKey, UInt256 value) {
    return new ResourceAmount().resourceIdentifier(stakeUnit(validatorKey)).value(value.toString());
  }

  public ResourceAmount nativeTokenAmount(UInt256 value) {
    return nativeTokenAmount(true, value);
  }

  public TokenResourceIdentifier nativeToken() {
    return create(REAddr.ofNativeToken(), "xrd");
  }

  public ResourceIdentifier stakeUnit(ECPublicKey validatorKey) {
    return new StakeUnitResourceIdentifier()
        .validatorAddress(addressing.forValidators().of(validatorKey))
        .type(STAKE_UNIT_TYPE);
  }

  public ResourceIdentifier resourceIdentifier(
      Bucket bucket, Function<REAddr, String> tokenAddressToSymbol) {
    if (bucket.resourceAddr() != null) {
      var addr = bucket.resourceAddr();
      var symbol = tokenAddressToSymbol.apply(addr);
      return new TokenResourceIdentifier()
          .rri(addressing.forResources().of(symbol, addr))
          .type(TOKEN_TYPE);
    }

    return new StakeUnitResourceIdentifier()
        .validatorAddress(addressing.forValidators().of(bucket.getValidatorKey()))
        .type(STAKE_UNIT_TYPE);
  }

  public ResourceAmount resourceOperation(
      Bucket bucket, UInt384 amount, Function<REAddr, String> tokenAddressToSymbol) {
    return new ResourceAmount()
        .resourceIdentifier(resourceIdentifier(bucket, tokenAddressToSymbol))
        .value(amount.toString());
  }

  public FeeTable feeTable(com.radixdlt.application.system.FeeTable feeTable) {
    var dto = new com.radixdlt.api.core.openapitools.model.FeeTable();
    feeTable
        .getPerUpSubstateFee()
        .forEach(
            (p, fee) ->
                dto.addPerUpSubstateFeeItem(
                    new UpSubstateFeeEntry()
                        .substateTypeIdentifier(substateTypeIdentifier(p))
                        .fee(nativeTokenAmount(fee))));
    dto.perByteFee(nativeTokenAmount(feeTable.getPerByteFee()));
    return dto;
  }

  public EngineConfiguration engineConfiguration(RERulesConfig config) {
    return new EngineConfiguration()
        .nativeToken(nativeToken())
        .maximumMessageLength(config.maxMessageLen())
        .maximumValidatorFeeIncrease(ValidatorUpdateRakeConstraintScrypt.MAX_RAKE_INCREASE)
        .feeTable(feeTable(config.feeTable()))
        .reservedSymbols(config.reservedSymbols().stream().toList())
        .tokenSymbolPattern(config.tokenSymbolPattern().pattern())
        .maximumTransactionSize(config.maxTxnSize())
        .maximumTransactionsPerRound(config.maxSigsPerRound().orElse(0))
        .maximumRoundsPerEpoch(config.maxRounds())
        .validatorFeeIncreaseDebouncerEpochLength(config.rakeIncreaseDebouncerEpochLength())
        .minimumStake(nativeTokenAmount(config.minimumStake().toSubunits()))
        .unstakingDelayEpochLength(config.unstakingEpochDelay())
        .rewardsPerProposal(nativeTokenAmount(config.rewardsPerProposal().toSubunits()))
        .minimumCompletedProposalsPercentage(config.minimumCompletedProposalsPercentage())
        .maximumValidators(config.maxValidators());
  }

  public Fork fork(ForkConfig forkConfig) {
    return new Fork()
        .name(forkConfig.name())
        .isCandidate(forkConfig instanceof CandidateForkConfig)
        .engineIdentifier(
            new EngineIdentifier().engine(forkConfig.engineRules().version().name().toLowerCase()))
        .engineConfiguration(engineConfiguration(forkConfig.engineRules().config()));
  }

  public DataObject tokenData(TokenResource tokenResource) {
    var tokenData =
        new TokenData()
            .granularity(tokenResource.granularity().toString())
            .isMutable(tokenResource.isMutable());
    tokenResource
        .optionalOwner()
        .map(REAddr::ofPubKeyAccount)
        .ifPresent(addr -> tokenData.setOwner(entityIdentifier(addr)));
    return tokenData.type(SubstateTypeMapping.getName(SubstateTypeId.TOKEN_RESOURCE));
  }

  public DataObject tokenMetadata(TokenResourceMetadata tokenResourceMetadata) {
    return new TokenMetadata()
        .symbol(tokenResourceMetadata.symbol())
        .name(tokenResourceMetadata.name())
        .description(tokenResourceMetadata.description())
        .url(tokenResourceMetadata.url())
        .iconUrl(tokenResourceMetadata.iconUrl())
        .type(SubstateTypeMapping.getName(SubstateTypeId.TOKEN_RESOURCE_METADATA));
  }

  public DataObject epochData(EpochData epochData) {
    return new com.radixdlt.api.core.openapitools.model.EpochData()
        .epoch(epochData.epoch())
        .type(SubstateTypeMapping.getName(SubstateTypeId.EPOCH_DATA));
  }

  public DataObject roundData(RoundData roundData) {
    return new com.radixdlt.api.core.openapitools.model.RoundData()
        .round(roundData.view())
        .timestamp(roundData.timestamp())
        .type(SubstateTypeMapping.getName(SubstateTypeId.ROUND_DATA));
  }

  public DataObject preparedValidatorRegistered(ValidatorRegisteredCopy copy) {
    var preparedValidatorRegistered = new PreparedValidatorRegistered();
    copy.epochUpdate().ifPresent(preparedValidatorRegistered::epoch);
    return preparedValidatorRegistered
        .registered(copy.isRegistered())
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY));
  }

  public DataObject preparedValidatorOwner(ValidatorOwnerCopy copy, boolean virtual) {
    var preparedValidatorOwner = new PreparedValidatorOwner();
    copy.epochUpdate().ifPresent(preparedValidatorOwner::epoch);
    return preparedValidatorOwner
        .owner(virtual ? selfAccountEntityIdentifier() : entityIdentifier(copy.owner()))
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_OWNER_COPY));
  }

  public DataObject preparedValidatorFee(ValidatorFeeCopy copy) {
    var preparedValidatorFee = new PreparedValidatorFee();
    copy.epochUpdate().ifPresent(preparedValidatorFee::epoch);
    return preparedValidatorFee
        .fee(copy.curRakePercentage())
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_RAKE_COPY));
  }

  public DataObject validatorMetadata(ValidatorMetaData metaData) {
    var validatorMetadata = new ValidatorMetadata();
    return validatorMetadata
        .name(metaData.name())
        .url(metaData.url())
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_META_DATA));
  }

  public DataObject validatorBFTData(ValidatorBFTData validatorBFTData) {
    var bftData = new com.radixdlt.api.core.openapitools.model.ValidatorBFTData();
    return bftData
        .proposalsCompleted(validatorBFTData.completedProposals())
        .proposalsMissed(validatorBFTData.missedProposals())
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_BFT_DATA));
  }

  public DataObject allowDelegationFlag(AllowDelegationFlag allowDelegationFlag) {
    var allowDelegation = new ValidatorAllowDelegation();
    return allowDelegation
        .allowDelegation(allowDelegationFlag.allowsDelegation())
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG));
  }

  public DataObject validatorSystemMetadata(ValidatorSystemMetadata validatorSystemMetadata) {
    var systemMetadata = new com.radixdlt.api.core.openapitools.model.ValidatorSystemMetadata();
    return systemMetadata
        .data(Bytes.toHexString(validatorSystemMetadata.data()))
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_SYSTEM_META_DATA));
  }

  private EntityIdentifier selfAccountEntityIdentifier() {
    return new EntityIdentifier().address(addressing.forAccounts().getHrp() + "1<self>");
  }

  public DataObject validatorStakeData(ValidatorStakeData validatorStakeData, boolean virtual) {
    var validatorData = new com.radixdlt.api.core.openapitools.model.ValidatorData();
    return validatorData
        .owner(
            virtual
                ? selfAccountEntityIdentifier()
                : entityIdentifier(validatorStakeData.ownerAddr()))
        .registered(validatorStakeData.isRegistered())
        .fee(validatorStakeData.rakePercentage())
        .type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_STAKE_DATA));
  }

  public DataObject unclaimedREAddrData() {
    var data = new UnclaimedRadixEngineAddress();
    return data.type(SubstateTypeMapping.getName(SubstateTypeId.UNCLAIMED_READDR));
  }

  private EntitySetIdentifier validatorsEntitySetIdentifier() {
    var regex =
        addressing.forValidators().getHrp()
            + "1[023456789ACDEFGHJKLMNPQRSTUVWXYZacdefghjklmnpqrstuvwxyz]{6,90}";
    return new EntitySetIdentifier().addressRegex(regex);
  }

  private EntitySetIdentifier tokensEntitySetIdentifier() {
    var regex =
        "[a-z0-9]+"
            + addressing.forResources().getHrpSuffix()
            + "1[023456789ACDEFGHJKLMNPQRSTUVWXYZacdefghjklmnpqrstuvwxyz]{6,90}";
    return new EntitySetIdentifier().addressRegex(regex);
  }

  public DataObject virtualParent(VirtualParent virtualParent) {
    var childType = SubstateTypeId.valueOf(virtualParent.data()[0]);
    var virtualDataObject =
        switch (childType) {
          case UNCLAIMED_READDR -> unclaimedREAddrData();
          case VALIDATOR_META_DATA -> validatorMetadata(
              ValidatorMetaData.createVirtual(MOCK_PUBLIC_KEY));
          case VALIDATOR_STAKE_DATA -> validatorStakeData(
              ValidatorStakeData.createVirtual(MOCK_PUBLIC_KEY), true);
          case VALIDATOR_ALLOW_DELEGATION_FLAG -> allowDelegationFlag(
              AllowDelegationFlag.createVirtual(MOCK_PUBLIC_KEY));
          case VALIDATOR_REGISTERED_FLAG_COPY -> preparedValidatorRegistered(
              ValidatorRegisteredCopy.createVirtual(MOCK_PUBLIC_KEY));
          case VALIDATOR_RAKE_COPY -> preparedValidatorFee(
              ValidatorFeeCopy.createVirtual(MOCK_PUBLIC_KEY));
          case VALIDATOR_OWNER_COPY -> preparedValidatorOwner(
              ValidatorOwnerCopy.createVirtual(MOCK_PUBLIC_KEY), true);
          case VALIDATOR_SYSTEM_META_DATA -> validatorSystemMetadata(
              ValidatorSystemMetadata.createVirtual(MOCK_PUBLIC_KEY));
          default -> throw new IllegalStateException(
              "Virtualization of " + childType + " unsupported");
        };

    var entitySetIdentifier =
        childType == SubstateTypeId.UNCLAIMED_READDR
            ? tokensEntitySetIdentifier()
            : validatorsEntitySetIdentifier();

    return new VirtualParentData()
        .entitySetIdentifier(entitySetIdentifier)
        .virtualDataObject(virtualDataObject)
        .type(SubstateTypeMapping.getName(SubstateTypeId.VIRTUAL_PARENT));
  }

  public Optional<DataObject> dataObject(Particle substate) {
    final DataObject dataObject;
    if (substate instanceof TokenResource tokenResource) {
      dataObject = tokenData(tokenResource);
    } else if (substate instanceof TokenResourceMetadata metadata) {
      dataObject = tokenMetadata(metadata);
    } else if (substate instanceof EpochData epochData) {
      dataObject = epochData(epochData);
    } else if (substate instanceof RoundData roundData) {
      dataObject = roundData(roundData);
    } else if (substate instanceof ValidatorRegisteredCopy validatorRegisteredCopy) {
      dataObject = preparedValidatorRegistered(validatorRegisteredCopy);
    } else if (substate instanceof ValidatorOwnerCopy validatorOwnerCopy) {
      dataObject = preparedValidatorOwner(validatorOwnerCopy, false);
    } else if (substate instanceof ValidatorFeeCopy validatorFeeCopy) {
      dataObject = preparedValidatorFee(validatorFeeCopy);
    } else if (substate instanceof ValidatorMetaData validatorMetaData) {
      dataObject = validatorMetadata(validatorMetaData);
    } else if (substate instanceof ValidatorBFTData validatorBFTData) {
      dataObject = validatorBFTData(validatorBFTData);
    } else if (substate instanceof AllowDelegationFlag allowDelegationFlag) {
      dataObject = allowDelegationFlag(allowDelegationFlag);
    } else if (substate instanceof ValidatorSystemMetadata validatorSystemMetadata) {
      dataObject = validatorSystemMetadata(validatorSystemMetadata);
    } else if (substate instanceof ValidatorStakeData validatorStakeData) {
      dataObject = validatorStakeData(validatorStakeData, false);
    } else if (substate instanceof UnclaimedREAddr) {
      dataObject = unclaimedREAddrData();
    } else if (substate instanceof VirtualParent virtualParent) {
      dataObject = virtualParent(virtualParent);
    } else {
      return Optional.empty();
    }

    return Optional.of(dataObject);
  }

  public SubstateIdentifier substateIdentifier(SubstateId substateId) {
    return new SubstateIdentifier().identifier(Bytes.toHexString(substateId.asBytes()));
  }

  public Substate substate(SubstateId substateId, boolean bootUp) {
    return new Substate()
        .substateIdentifier(substateIdentifier(substateId))
        .substateOperation(
            bootUp
                ? Substate.SubstateOperationEnum.BOOTUP
                : Substate.SubstateOperationEnum.SHUTDOWN);
  }

  public ResourceAmount resourceOperation(
      ResourceInBucket resourceInBucket, boolean bootUp, Function<REAddr, String> addressToSymbol) {
    var amount = new BigInteger(bootUp ? 1 : -1, resourceInBucket.amount().toByteArray());
    var bucket = resourceInBucket.bucket();
    var resourceIdentifier = resourceIdentifier(bucket, addressToSymbol);
    return new ResourceAmount().resourceIdentifier(resourceIdentifier).value(amount.toString());
  }

  private EntityIdentifier entityIdentifierOwnedBucket(ResourceInBucket resourceInBucket) {
    var entityIdentifier = new EntityIdentifier();
    var bucket = resourceInBucket.bucket();
    entityIdentifier.address(addressing.forAccounts().of(bucket.getOwner()));
    if (bucket.getValidatorKey() != null) {
      if (bucket.resourceAddr() != null && bucket.getEpochUnlock() == null) {
        entityIdentifier.subEntity(
            new SubEntity()
                .address(PREPARED_STAKES_ADDRESS)
                .metadata(
                    new SubEntityMetadata()
                        .validatorAddress(
                            addressing.forValidators().of(bucket.getValidatorKey()))));
      } else if (bucket.resourceAddr() == null && Objects.equals(bucket.getEpochUnlock(), 0L)) {
        // Don't add validator as validator is already part of resource
        entityIdentifier.subEntity(new SubEntity().address(PREPARED_UNSTAKES_ADDRESS));
      } else if (bucket.resourceAddr() != null && bucket.getEpochUnlock() != null) {
        entityIdentifier.subEntity(
            new SubEntity()
                .address(EXITING_UNSTAKES_ADDRESS)
                .metadata(
                    new SubEntityMetadata()
                        .validatorAddress(addressing.forValidators().of(bucket.getValidatorKey()))
                        .epochUnlock(bucket.getEpochUnlock())));
      }
    }
    return entityIdentifier;
  }

  private EntityIdentifier entityIdentifier(
      Particle substate, Function<REAddr, String> addressToSymbol) {
    if (substate instanceof ResourceInBucket resourceInBucket
        && resourceInBucket.bucket().getOwner() != null) {
      return entityIdentifierOwnedBucket(resourceInBucket);
    } else if (substate instanceof ValidatorStakeData validatorStakeData) {
      return new EntityIdentifier()
          .address(addressing.forValidators().of(validatorStakeData.validatorKey()))
          .subEntity(new SubEntity().address(SYSTEM_ADDRESS));
    } else if (substate instanceof ResourceData resourceData) {
      var symbol = addressToSymbol.apply(resourceData.addr());
      return new EntityIdentifier()
          .address(addressing.forResources().of(symbol, resourceData.addr()));
    } else if (substate instanceof SystemData) {
      return new EntityIdentifier().address(SYSTEM_ADDRESS);
    } else if (substate instanceof ValidatorUpdatingData validatorUpdatingData) {
      return new EntityIdentifier()
          .address(addressing.forValidators().of(validatorUpdatingData.validatorKey()));
    } else if (substate
        instanceof com.radixdlt.application.validators.state.ValidatorData validatorData) {
      return new EntityIdentifier()
          .address(addressing.forValidators().of(validatorData.validatorKey()))
          .subEntity(new SubEntity().address(SYSTEM_ADDRESS));
    } else if (substate instanceof UnclaimedREAddr unclaimedREAddr) {
      var addr = unclaimedREAddr.addr();
      final String address;
      if (addr.isSystem()) {
        address = SYSTEM_ADDRESS;
      } else {
        var symbol = addressToSymbol.apply(addr);
        address = addressing.forResources().of(symbol, addr);
      }
      return new EntityIdentifier().address(address);
    } else if (substate instanceof VirtualParent) {
      return new EntityIdentifier().address(SYSTEM_ADDRESS);
    } else {
      throw new IllegalStateException("Unknown substate " + substate);
    }
  }

  public Optional<Data> data(Particle substate, boolean bootUp) {
    return dataObject(substate)
        .map(
            dataObject ->
                new Data()
                    .dataObject(dataObject)
                    .action(bootUp ? Data.ActionEnum.CREATE : Data.ActionEnum.DELETE));
  }

  public Operation operation(
      Particle substate,
      SubstateId substateId,
      boolean isBootUp,
      Function<REAddr, String> addressToSymbol) {
    var operation = new Operation();
    var typeId = SubstateTypeId.valueOf(substate.getClass());
    operation.type(SubstateTypeMapping.getType(typeId));
    operation.substate(substate(substateId, isBootUp));
    operation.entityIdentifier(entityIdentifier(substate, addressToSymbol));
    if (substate instanceof ResourceInBucket resourceInBucket
        && !resourceInBucket.amount().isZero()) {
      operation.amount(resourceOperation(resourceInBucket, isBootUp, addressToSymbol));
    }
    data(substate, isBootUp).ifPresent(operation::data);
    return operation;
  }

  public Operation operation(REStateUpdate update, Function<REAddr, String> addressToSymbol) {
    var operation = new Operation();
    var substate = (Particle) update.getParsed();
    operation.type(SubstateTypeMapping.getType(SubstateTypeId.valueOf(update.typeByte())));
    operation.substate(substate(update.getId(), update.isBootUp()));
    operation.entityIdentifier(entityIdentifier(substate, addressToSymbol));
    if (substate instanceof ResourceInBucket resourceInBucket
        && !resourceInBucket.amount().isZero()) {
      operation.amount(resourceOperation(resourceInBucket, update.isBootUp(), addressToSymbol));
    }
    data(substate, update.isBootUp()).ifPresent(operation::data);
    return operation;
  }

  public OperationGroup operationGroup(
      List<REStateUpdate> stateUpdates, Function<REAddr, String> addressToSymbol) {
    var operationGroup = new OperationGroup();
    stateUpdates.forEach(u -> operationGroup.addOperationsItem(operation(u, addressToSymbol)));
    return operationGroup;
  }

  public CommittedTransaction committedTransaction(
      REProcessedTxn txn,
      AccumulatorState accumulatorState,
      Function<REAddr, String> addressToSymbol) {
    Function<REAddr, String> localizedAddressToSymbol =
        addr -> {
          var localSymbol =
              txn.getGroupedStateUpdates().stream()
                  .flatMap(List::stream)
                  .map(REStateUpdate::getParsed)
                  .filter(TokenResourceMetadata.class::isInstance)
                  .map(TokenResourceMetadata.class::cast)
                  .filter(r -> r.addr().equals(addr))
                  .map(TokenResourceMetadata::symbol)
                  .findFirst();

          return localSymbol.orElseGet(() -> addressToSymbol.apply(addr));
        };

    var transaction = new CommittedTransaction();

    for (var stateUpdates : txn.getGroupedStateUpdates()) {
      var operationGroup = operationGroup(stateUpdates, localizedAddressToSymbol);
      transaction.addOperationGroupsItem(operationGroup);
    }

    var metadata =
        new CommittedTransactionMetadata()
            .fee(nativeTokenAmount(txn.getFeePaid()))
            .hex(Bytes.toHexString(txn.getTxn().getPayload()))
            .size(txn.getTxn().getPayload().length);
    txn.getSignedBy().ifPresent(s -> metadata.setSignedBy(publicKey(s)));
    txn.getMsg().ifPresent(msg -> metadata.setMessage(Bytes.toHexString(msg)));

    transaction.metadata(metadata);
    transaction.transactionIdentifier(transactionIdentifier(txn.getTxnId()));
    transaction.committedStateIdentifier(stateIdentifier(accumulatorState));

    return transaction;
  }

  public Transaction transaction(REProcessedTxn txn, Function<REAddr, String> addressToSymbol) {
    Function<REAddr, String> localizedAddressToSymbol =
        addr -> {
          var localSymbol =
              txn.getGroupedStateUpdates().stream()
                  .flatMap(List::stream)
                  .map(REStateUpdate::getParsed)
                  .filter(TokenResourceMetadata.class::isInstance)
                  .map(TokenResourceMetadata.class::cast)
                  .filter(r -> r.addr().equals(addr))
                  .map(TokenResourceMetadata::symbol)
                  .findFirst();

          return localSymbol.orElseGet(() -> addressToSymbol.apply(addr));
        };

    var transaction = new Transaction();

    for (var stateUpdates : txn.getGroupedStateUpdates()) {
      var operationGroup = operationGroup(stateUpdates, localizedAddressToSymbol);
      transaction.addOperationGroupsItem(operationGroup);
    }

    var metadata =
        new CommittedTransactionMetadata()
            .fee(nativeTokenAmount(txn.getFeePaid()))
            .message(txn.getMsg().map(Bytes::toHexString).orElse(null));
    transaction.metadata(metadata);

    // If user transaction is signed then we can return back complete information
    txn.getSignedBy()
        .ifPresent(
            publicKey -> {
              metadata.signedBy(publicKey(publicKey));
              metadata.size(txn.getTxn().getPayload().length);
              metadata.hex(Bytes.toHexString(txn.getTxn().getPayload()));
              transaction.transactionIdentifier(transactionIdentifier(txn.getTxnId()));
            });

    return transaction;
  }

  public TransactionIdentifier transactionIdentifier(AID txnId) {
    return new TransactionIdentifier().hash(txnId.toString());
  }

  public CoreApiException notValidatorEntityException(EntityIdentifier entityIdentifier) {
    return CoreApiException.badRequest(
        new NotValidatorEntityError()
            .entity(entityIdentifier)
            .type(NotValidatorEntityError.class.getSimpleName()));
  }

  public CoreApiException parseException(TxnParseException exception) {
    var cause = Throwables.getRootCause(exception);
    return CoreApiException.badRequest(
        new InvalidTransactionError()
            .message(cause.getMessage())
            .type(InvalidTransactionError.class.getSimpleName()));
  }

  public CoreApiException mempoolFullException(MempoolFullException e) {
    return CoreApiException.unavailable(
        new MempoolFullError()
            .mempoolTransactionCount(e.getMaxSize())
            .type(MempoolFullError.class.getSimpleName()));
  }

  public CoreApiException radixEngineException(RadixEngineException exception) {
    var cause = Throwables.getRootCause(exception);
    if (cause instanceof SubstateNotFoundException notFoundException) {
      return CoreApiException.conflict(
          new SubstateDependencyNotFoundError()
              .substateIdentifierNotFound(substateIdentifier(notFoundException.getSubstateId()))
              .type(SubstateDependencyNotFoundError.class.getSimpleName()));
    }

    return CoreApiException.badRequest(
        new InvalidTransactionError()
            .message(cause.getMessage())
            .type(InvalidTransactionError.class.getSimpleName()));
  }

  public ForkVotingResult forkVotingResult(
      com.radixdlt.statecomputer.forks.ForkVotingResult forkVotingResult) {
    return new ForkVotingResult()
        .epoch(forkVotingResult.epoch())
        .candidateForkId(forkVotingResult.candidateForkId().toString())
        .stakePercentageVoted((float) forkVotingResult.stakePercentageVoted() / 100);
  }
}
