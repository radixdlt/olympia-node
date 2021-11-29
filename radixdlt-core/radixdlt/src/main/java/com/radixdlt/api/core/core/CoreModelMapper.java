/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.core.core;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.api.core.core.model.DataOperation;
import com.radixdlt.api.core.core.model.Entity;
import com.radixdlt.api.core.core.model.ResourceOperation;
import com.radixdlt.api.core.core.model.entities.AccountVaultEntity;
import com.radixdlt.api.core.core.model.EntityOperation;
import com.radixdlt.api.core.core.model.entities.EntityDoesNotSupportResourceDepositException;
import com.radixdlt.api.core.core.model.entities.EntityDoesNotSupportResourceWithdrawException;
import com.radixdlt.api.core.core.model.entities.InvalidDataObjectException;
import com.radixdlt.api.core.core.model.exceptions.AboveMaximumValidatorFeeIncreaseException;
import com.radixdlt.api.core.core.model.exceptions.BelowMinimumStakeException;
import com.radixdlt.api.core.core.model.entities.EntityDoesNotSupportDataObjectException;
import com.radixdlt.api.core.core.model.exceptions.DataObjectNotSupportedByEntityException;
import com.radixdlt.api.core.core.model.exceptions.InvalidAddressException;
import com.radixdlt.api.core.core.model.exceptions.InvalidFeePayerEntityException;
import com.radixdlt.api.core.core.model.exceptions.InvalidHexException;
import com.radixdlt.api.core.core.model.exceptions.InvalidPartialStateIdentifierException;
import com.radixdlt.api.core.core.model.exceptions.InvalidPublicKeyException;
import com.radixdlt.api.core.core.model.exceptions.InvalidSignatureException;
import com.radixdlt.api.core.core.model.exceptions.InvalidSubEntityException;
import com.radixdlt.api.core.core.model.OperationTxBuilder;
import com.radixdlt.api.core.core.model.entities.PreparedStakeVaultEntity;
import com.radixdlt.api.core.core.model.entities.PreparedUnstakeVaultEntity;
import com.radixdlt.api.core.core.model.Resource;
import com.radixdlt.api.core.core.model.StakeOwnershipResource;
import com.radixdlt.api.core.core.model.entities.TokenEntity;
import com.radixdlt.api.core.core.model.entities.ValidatorEntity;
import com.radixdlt.api.core.core.model.exceptions.InvalidTransactionHashException;
import com.radixdlt.api.core.core.model.exceptions.NetworkNotSupportedException;
import com.radixdlt.api.core.core.model.exceptions.NotValidatorOwnerException;
import com.radixdlt.api.core.core.model.exceptions.ResourceDepositNotSupportedByEntityException;
import com.radixdlt.api.core.core.model.exceptions.ResourceWithdrawNotSupportedByEntityException;
import com.radixdlt.api.core.core.openapitools.model.*;
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
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.application.validators.state.ValidatorUpdatingData;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;

import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class CoreModelMapper {
	private final Addressing addressing;
	private final Network network;
	private final Forks forks;

	@Inject
	CoreModelMapper(
		@NetworkId int networkId,
		Addressing addressing,
		Forks forks
	) {
		this.network = Network.ofId(networkId).orElseThrow();
		this.addressing = addressing;
		this.forks = forks;
	}

	public static class InvalidTransactionException extends CoreModelException {
		public InvalidTransactionException() {
			super(CoreModelError.BAD_REQUEST);
		}

		@Override
		public ErrorDetails getErrorDetails() {
			return null;
		}
	}

	public static class StateConflictException extends CoreModelException {
		public StateConflictException() {
			super(CoreModelError.STATE_CONFLICT);
		}

		@Override
		public ErrorDetails getErrorDetails() {
			return null;
		}
	}

	public void verifyNetwork(NetworkIdentifier networkIdentifier) throws NetworkNotSupportedException {
		if (!networkIdentifier.getNetwork().equals(this.network.name().toLowerCase())) {
			throw new NetworkNotSupportedException(this.network);
		}
	}

	public Pair<ECPublicKey, ECDSASignature> keyAndSignature(Signature signature) throws CoreModelException {
		var publicKey = ecPublicKey(signature.getPublicKey());
		var bytes = bytes(signature.getBytes());
		ECDSASignature sig;
		try {
			sig = ECDSASignature.decodeFromDER(bytes);
		} catch (IllegalArgumentException e) {
			throw new InvalidSignatureException(signature.getBytes());
		}
		return Pair.of(publicKey, sig);
	}

	public ECPublicKey ecPublicKey(PublicKey publicKey) throws CoreModelException {
		var bytes = bytes(publicKey.getHex());
		try {
			return ECPublicKey.fromBytes(bytes);
		} catch (PublicKeyException e) {
			throw new InvalidPublicKeyException(publicKey);
		}
	}

	public byte[] bytes(String hex) throws InvalidHexException {
		try {
			return Bytes.fromHexString(hex);
		} catch (IllegalArgumentException e) {
			throw new InvalidHexException(hex);
		}
	}

	public CoreModelException coreModelException(TxBuilderException e) {
		if (e instanceof MinimumStakeException minimumStakeException) {
			return new BelowMinimumStakeException(
				nativeTokenAmount(minimumStakeException.getMinimumStake()),
				nativeTokenAmount(minimumStakeException.getAttempt())
			);
		} else if (e instanceof DelegateStakePermissionException delegateException) {
			return new NotValidatorOwnerException(
				entityIdentifier(delegateException.getOwner()),
				entityIdentifier(delegateException.getUser())
			);
		} else if (e instanceof InvalidDataObjectException invalidDataObjectException) {
			return new com.radixdlt.api.core.core.model.exceptions.InvalidDataObjectException(
				invalidDataObjectException.getDataObject().getDataObject(),
				invalidDataObjectException.getMessage()
			);
		} else if (e instanceof InvalidRakeIncreaseException rakeIncreaseException) {
			return new AboveMaximumValidatorFeeIncreaseException(
				rakeIncreaseException.getMaxRakeIncrease(),
				rakeIncreaseException.getIncreaseAttempt()
			);
		} else if (e instanceof EntityDoesNotSupportDataObjectException dataObjectException) {
			return new DataObjectNotSupportedByEntityException(
				dataObjectException.getDataObject().getDataObject(),
				entityIdentifier(dataObjectException.getEntity())
			);
		} else if (e instanceof EntityDoesNotSupportResourceDepositException depositException) {
			return new ResourceDepositNotSupportedByEntityException(
				resourceIdentifier(depositException.getResource()),
				entityIdentifier(depositException.getEntity())
			);
		} else if (e instanceof EntityDoesNotSupportResourceWithdrawException withdrawException) {
			return new ResourceWithdrawNotSupportedByEntityException(
				resourceIdentifier(withdrawException.getResource()),
				entityIdentifier(withdrawException.getEntity())
			);
		}

		throw new IllegalStateException(e);
	}

	public AccountVaultEntity feePayerEntity(EntityIdentifier entityIdentifier) throws CoreModelException {
		var feePayer = entity(entityIdentifier);
		if (!(feePayer instanceof AccountVaultEntity accountVaultEntity)) {
			throw new InvalidFeePayerEntityException(entityIdentifier);
		}
		return accountVaultEntity;
	}

	public Entity entity(EntityIdentifier entityIdentifier) throws CoreModelException {
		var address = entityIdentifier.getAddress();
		var addressType = addressing.getAddressType(address).orElseThrow(() -> new InvalidAddressException(address));
		switch (addressType) {
			case VALIDATOR -> {
				var key = addressing.forValidators().parseOrThrow(address, s -> new InvalidAddressException(address));
				return ValidatorEntity.from(key);
				// TODO: Add Validator System entity
			}
			case ACCOUNT -> {
				var accountAddress = addressing.forAccounts().parseOrThrow(address, s -> new InvalidAddressException(address));
				var subEntity = entityIdentifier.getSubEntity();
				if (subEntity == null) {
					return AccountVaultEntity.from(accountAddress);
				}

				var metadata = subEntity.getMetadata();
				if (metadata == null) {
					throw new InvalidSubEntityException(subEntity);
				}

				var validator = addressing.forValidators().parseOrThrow(metadata.getValidator(), s -> new InvalidAddressException(address));
				return switch (subEntity.getAddress()) {
					case "prepared_stake" -> PreparedStakeVaultEntity.from(
						accountAddress,
						validator
					);
					case "prepared_unstake" -> PreparedUnstakeVaultEntity.from(
						accountAddress,
						validator
					);
					default -> throw new InvalidSubEntityException(subEntity);
				};
			}
			case RESOURCE -> {
				var pair = addressing.forResources().parseOrThrow(address, s -> new InvalidAddressException(address));
				return TokenEntity.from(pair.getFirst(), pair.getSecond());
			}
			default -> throw new IllegalStateException("Unknown addressType: " + addressType);
		}
	}

	public DataOperation dataOperation(Data data) throws CoreModelException {
		if (data == null) {
			return null;
		}

		final ClassToInstanceMap<Object> parsed;
		var dataObject = data.getDataObject();
		if (dataObject instanceof PreparedValidatorOwner preparedValidatorOwner) {
			var owner = preparedValidatorOwner.getOwner();
			parsed = ImmutableClassToInstanceMap.of(
				REAddr.class, addressing.forAccounts().parseOrThrow(owner, s -> new InvalidAddressException(owner))
			);
		} else if (dataObject instanceof TokenData tokenData && tokenData.getOwner() != null) {
			var owner = tokenData.getOwner();
			var ownerAddress = addressing.forAccounts().parseOrThrow(owner, s -> new InvalidAddressException(owner));
			var key = ownerAddress.publicKey().orElseThrow(() -> new InvalidAddressException(owner));
			parsed = ImmutableClassToInstanceMap.of(ECPublicKey.class, key);
		} else {
			parsed = ImmutableClassToInstanceMap.of();
		}

		return new DataOperation(data, parsed);
	}

	public OperationTxBuilder operationTxBuilder(String message, List<OperationGroup> operationGroups) throws CoreModelException {
		var entityOperationGroups = new ArrayList<List<EntityOperation>>();
		for (var group : operationGroups) {
			var entityOperationGroup = new ArrayList<EntityOperation>();
			for (var op : group.getOperations()) {
				var entityOperation = new EntityOperation(
					entity(op.getEntityIdentifier()),
					resourceOperation(op.getAmount()),
					dataOperation(op.getData())
				);
				entityOperationGroup.add(entityOperation);
			}
			entityOperationGroups.add(entityOperationGroup);
		}

		return new OperationTxBuilder(message, entityOperationGroups, forks);
	}


	public Txn txn(String hex) throws InvalidHexException {
		var bytes = bytes(hex);
		return Txn.create(bytes);
	}

	public AID txnId(TransactionIdentifier transactionIdentifier) throws InvalidTransactionHashException {
		var hash = transactionIdentifier.getHash();
		try {
			return AID.from(hash);
		} catch (IllegalArgumentException e) {
			throw new InvalidTransactionHashException(hash);
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

	public Pair<Long, HashCode> partialStateIdentifier(PartialStateIdentifier partialStateIdentifier) throws InvalidPartialStateIdentifierException {
		if (partialStateIdentifier == null) {
			return Pair.of(0L, null);
		}

		if (partialStateIdentifier.getStateVersion() < 0L) {
			throw new InvalidPartialStateIdentifierException(partialStateIdentifier);
		}

		final HashCode accumulator;
		if (partialStateIdentifier.getTransactionAccumulator() != null) {
			try {
				var bytes = Bytes.fromHexString(partialStateIdentifier.getTransactionAccumulator());
				accumulator = HashCode.fromBytes(bytes);
			} catch (IllegalArgumentException e) {
				throw new InvalidPartialStateIdentifierException(partialStateIdentifier);
			}
		} else {
			accumulator = null;
		}

		return Pair.of(partialStateIdentifier.getStateVersion(), accumulator);
	}

	public ResourceOperation resourceOperation(ResourceAmount resourceAmount) throws InvalidAddressException {
		if (resourceAmount == null) {
			return null;
		}

		var bigInteger = new BigInteger(resourceAmount.getValue());
		var isPositive = bigInteger.compareTo(BigInteger.ZERO) > 0;

		return new ResourceOperation(
			resource(resourceAmount.getResourceIdentifier()),
			UInt256.from((isPositive ? bigInteger : bigInteger.negate()).toByteArray()),
			isPositive
		);
	}

	public Resource resource(ResourceIdentifier resourceIdentifier) throws InvalidAddressException {
		if (resourceIdentifier instanceof TokenResourceIdentifier tokenResourceIdentifier) {
			var rri = tokenResourceIdentifier.getRri();
			var symbolAndAddr = addressing.forResources().parseOrThrow(rri, s -> new InvalidAddressException(rri));
			return com.radixdlt.api.core.core.model.TokenResource.from(symbolAndAddr.getFirst(), symbolAndAddr.getSecond());
		} else if (resourceIdentifier instanceof StakeOwnershipResourceIdentifier stakeOwnershipResourceIdentifier) {
			var validatorAddress = stakeOwnershipResourceIdentifier.getValidator();
			var key = addressing.forValidators().parseOrThrow(validatorAddress, s -> new InvalidAddressException(validatorAddress));
			return StakeOwnershipResource.from(key);
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
		if (resource instanceof com.radixdlt.api.core.core.model.TokenResource tokenResource) {
			return new TokenResourceIdentifier()
				.rri(addressing.forResources().of(tokenResource.getSymbol(), tokenResource.getTokenAddress()))
				.type("TokenResourceIdentifier");
		} else if (resource instanceof StakeOwnershipResource stakeOwnership) {
			return new StakeOwnershipResourceIdentifier()
				.validator(addressing.forValidators().of(stakeOwnership.getValidatorKey()))
				.type("StakeOwnershipResourceIdentifier");
		} else {
			throw new IllegalStateException("Unknown resource " + resource);
		}
	}

	public EntityIdentifier entityIdentifier(Entity entity) {
		if (entity instanceof AccountVaultEntity account) {
			return entityIdentifier(account.getAccountAddress());
		} else if (entity instanceof PreparedStakeVaultEntity stake) {
			return entityIdentifier(stake.getAccountAddress())
				.subEntity(new SubEntity()
					.address("prepared_stake")
					.metadata(new SubEntityMetadata()
						.validator(addressing.forValidators().of(stake.getValidatorKey()))
					)
				);
		} else if (entity instanceof PreparedUnstakeVaultEntity unstake) {
			return entityIdentifier(unstake.getAccountAddress())
				.subEntity(new SubEntity()
					.address("prepared_unstake")
					.metadata(new SubEntityMetadata()
						.validator(addressing.forValidators().of(unstake.getValidatorKey()))
					)
				);
		} else if (entity instanceof ValidatorEntity validator) {
			return entityIdentifier(validator.getValidatorKey());
		} else if (entity instanceof TokenEntity tokenEntity) {
			return entityIdentifier(tokenEntity.getTokenAddr(), tokenEntity.getSymbol());
		} else {
			throw new IllegalStateException("Unkown entity: " + entity);
		}
	}

	public EntityIdentifier entityIdentifier(REAddr tokenAddress, String symbol) {
		return new EntityIdentifier().address(addressing.forResources().of(symbol, tokenAddress));
	}

	public EntityIdentifier entityIdentifier(REAddr accountAddress) {
		return new EntityIdentifier().address(addressing.forAccounts().of(accountAddress));
	}

	public EntityIdentifier entityIdentifier(ECPublicKey validatorKey) {
		return new EntityIdentifier().address(addressing.forValidators().of(validatorKey));
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
		return new EngineStateIdentifier()
			.stateIdentifier(stateIdentifier(ledgerProof.getAccumulatorState()))
			.epoch(ledgerProof.getEpoch())
			.round(ledgerProof.getView().number())
			.timestamp(ledgerProof.timestamp());
	}

	public SubstateTypeIdentifier substateTypeIdentifier(Class<? extends Particle> substateClass) {
		var name = SubstateTypeMapping.getName(SubstateTypeId.valueOf(substateClass));
		return new SubstateTypeIdentifier()
			.type(name);
	}

	public ResourceIdentifier create(REAddr tokenAddress, String symbol) {
		return new TokenResourceIdentifier()
			.rri(addressing.forResources().of(symbol, tokenAddress))
			.type("Token");
	}

	public ResourceAmount nativeTokenAmount(UInt256 value) {
		return new ResourceAmount()
			.resourceIdentifier(nativeToken())
			.value(value.toString());
	}

	public ResourceIdentifier nativeToken() {
		return create(REAddr.ofNativeToken(), "xrd");
	}

	public ResourceIdentifier resourceIdentifier(Bucket bucket, Function<REAddr, String> tokenAddressToSymbol) {
		if (bucket.resourceAddr() != null) {
			var addr = bucket.resourceAddr();
			var symbol = tokenAddressToSymbol.apply(addr);
			return new TokenResourceIdentifier()
				.rri(addressing.forResources().of(symbol, addr))
				.type("Token");
		}

		return new StakeOwnershipResourceIdentifier()
			.validator(addressing.forValidators().of(bucket.getValidatorKey()))
			.type("StakeOwnership");
	}

	public ResourceAmount resourceOperation(Bucket bucket, UInt384 amount, Function<REAddr, String> tokenAddressToSymbol) {
		return new ResourceAmount()
			.resourceIdentifier(resourceIdentifier(bucket, tokenAddressToSymbol))
			.value(amount.toString());
	}

	public FeeTable feeTable(com.radixdlt.application.system.FeeTable feeTable) {
		var dto = new com.radixdlt.api.core.core.openapitools.model.FeeTable();
		feeTable.getPerUpSubstateFee().forEach((p, fee) ->
			dto.addPerUpSubstateFeeItem(new UpSubstateFeeEntry()
				.substateTypeIdentifier(substateTypeIdentifier(p))
				.fee(nativeTokenAmount(fee))
			)
		);
		dto.perByteFee(nativeTokenAmount(feeTable.getPerByteFee()));
		return dto;
	}

	public EngineConfiguration engineConfiguration(RERulesConfig config) {
		return new EngineConfiguration()
			.feeTable(feeTable(config.getFeeTable()))
			.reservedSymbols(config.getReservedSymbols().stream().toList())
			.tokenSymbolPattern(config.getTokenSymbolPattern().pattern())
			.maximumTransactionSize(config.getMaxTxnSize())
			.maximumTransactionsPerRound(config.getMaxSigsPerRound().orElse(0))
			.maximumRoundsPerEpoch(config.getMaxRounds())
			.validatorFeeIncreaseDebouncerEpochLength(config.getRakeIncreaseDebouncerEpochLength())
			.minimumStake(nativeTokenAmount(config.getMinimumStake().toSubunits()))
			.unstakingDelayEpochLength(config.getUnstakingEpochDelay())
			.rewardsPerProposal(nativeTokenAmount(config.getRewardsPerProposal().toSubunits()))
			.minimumCompletedProposalsPercentage(config.getMinimumCompletedProposalsPercentage())
			.maximumValidators(config.getMaxValidators());
	}

	public Fork fork(ForkConfig forkConfig) {
		return new Fork()
			.forkIdentifier(new ForkIdentifier()
				.epoch(forkConfig.getEpoch())
				.fork(forkConfig.getName())
			)
			.engineIdentifier(new EngineIdentifier().engine(forkConfig.getVersion().name().toLowerCase()))
			.engineConfiguration(engineConfiguration(forkConfig.getConfig()));
	}

	public DataObject tokenData(TokenResource tokenResource) {
		var tokenData = new TokenData()
			.granularity(tokenResource.getGranularity().toString())
			.isMutable(tokenResource.isMutable());
		tokenResource.getOwner()
			.map(REAddr::ofPubKeyAccount)
			.ifPresent(key -> tokenData.setOwner(addressing.forAccounts().of(key)));
		return tokenData
			.type(SubstateTypeMapping.getName(SubstateTypeId.TOKEN_RESOURCE));
	}

	public DataObject tokenMetadata(TokenResourceMetadata tokenResourceMetadata) {
		return new TokenMetadata()
			.symbol(tokenResourceMetadata.getSymbol())
			.name(tokenResourceMetadata.getName())
			.description(tokenResourceMetadata.getDescription())
			.url(tokenResourceMetadata.getUrl())
			.iconUrl(tokenResourceMetadata.getIconUrl())
			.type(SubstateTypeMapping.getName(SubstateTypeId.TOKEN_RESOURCE_METADATA));
	}

	public DataObject epochData(EpochData epochData) {
		return new com.radixdlt.api.core.core.openapitools.model.EpochData()
			.epoch(epochData.getEpoch())
			.type(SubstateTypeMapping.getName(SubstateTypeId.EPOCH_DATA));
	}

	public DataObject roundData(RoundData roundData) {
		return new com.radixdlt.api.core.core.openapitools.model.RoundData()
			.round(roundData.getView())
			.timestamp(roundData.getTimestamp())
			.type(SubstateTypeMapping.getName(SubstateTypeId.ROUND_DATA));
	}

	public DataObject preparedValidatorRegistered(ValidatorRegisteredCopy copy) {
		var preparedValidatorRegistered = new PreparedValidatorRegistered();
		copy.getEpochUpdate().ifPresent(preparedValidatorRegistered::epoch);
		return preparedValidatorRegistered
			.registered(copy.isRegistered())
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY));
	}

	public DataObject preparedValidatorOwner(ValidatorOwnerCopy copy) {
		var preparedValidatorOwner = new PreparedValidatorOwner();
		copy.getEpochUpdate().ifPresent(preparedValidatorOwner::epoch);
		return preparedValidatorOwner
			.owner(addressing.forAccounts().of(copy.getOwner()))
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_OWNER_COPY));
	}

	public DataObject preparedValidatorFee(ValidatorFeeCopy copy) {
		var preparedValidatorFee = new PreparedValidatorFee();
		copy.getEpochUpdate().ifPresent(preparedValidatorFee::epoch);
		return preparedValidatorFee
			.fee(copy.getRakePercentage())
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_RAKE_COPY));
	}

	public DataObject validatorMetadata(ValidatorMetaData metaData) {
		var validatorMetadata = new ValidatorMetadata();
		return validatorMetadata
			.name(metaData.getName())
			.url(metaData.getUrl())
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_META_DATA));
	}

	public DataObject validatorBFTData(ValidatorBFTData validatorBFTData) {
		var bftData = new com.radixdlt.api.core.core.openapitools.model.ValidatorBFTData();
		return bftData
			.proposalsCompleted(validatorBFTData.proposalsCompleted())
			.proposalsMissed(validatorBFTData.proposalsMissed())
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_BFT_DATA));
	}

	public DataObject allowDelegationFlag(AllowDelegationFlag allowDelegationFlag) {
		var allowDelegation = new ValidatorAllowDelegation();
		return allowDelegation
			.allowDelegation(allowDelegationFlag.allowsDelegation())
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG));
	}

	public DataObject validatorSystemMetadata(ValidatorSystemMetadata validatorSystemMetadata) {
		var systemMetadata = new com.radixdlt.api.core.core.openapitools.model.ValidatorSystemMetadata();
		return systemMetadata
			.data(Bytes.toHexString(validatorSystemMetadata.getData()))
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_SYSTEM_META_DATA));
	}

	public DataObject validatorStakeData(ValidatorStakeData validatorStakeData) {
		var validatorData = new com.radixdlt.api.core.core.openapitools.model.ValidatorData();
		return validatorData
			.owner(addressing.forAccounts().of(validatorStakeData.getOwnerAddr()))
			.registered(validatorStakeData.isRegistered())
			.fee(validatorStakeData.getRakePercentage())
			.type(SubstateTypeMapping.getName(SubstateTypeId.VALIDATOR_STAKE_DATA));
	}

	public DataObject virtualParent(VirtualParent virtualParent) {
		var virtualParentData = new VirtualParentData();
		var childType = SubstateTypeId.valueOf(virtualParent.getData()[0]);
		return virtualParentData
			.childType(SubstateTypeMapping.getName(childType))
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
			dataObject = preparedValidatorOwner(validatorOwnerCopy);
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
			dataObject = validatorStakeData(validatorStakeData);
		} else {
			return Optional.empty();
		}

		return Optional.of(dataObject);
	}

	public SubstateIdentifier substateIdentifier(SubstateId substateId) {
		return new SubstateIdentifier()
			.identifier(Bytes.toHexString(substateId.asBytes()));
	}

	public Substate substate(SubstateId substateId, boolean bootUp) {
		return new Substate()
			.substateIdentifier(substateIdentifier(substateId))
			.substateOperation(bootUp ? Substate.SubstateOperationEnum.BOOTUP : Substate.SubstateOperationEnum.SHUTDOWN);
	}

	public ResourceAmount resourceOperation(ResourceInBucket resourceInBucket, boolean bootUp, Function<REAddr, String> addressToSymbol) {
		var amount = new BigInteger(bootUp ? 1 : -1, resourceInBucket.getAmount().toByteArray());
		var bucket = resourceInBucket.bucket();
		var resourceIdentifier = resourceIdentifier(bucket, addressToSymbol);
		return new ResourceAmount()
			.resourceIdentifier(resourceIdentifier)
			.value(amount.toString());
	}

	public EntityIdentifier entityIdentifier(Particle substate, Function<REAddr, String> addressToSymbol) {
		var entityIdentifier = new EntityIdentifier();
		if (substate instanceof ResourceInBucket resourceInBucket && resourceInBucket.bucket().getOwner() != null) {
			var bucket = resourceInBucket.bucket();
			entityIdentifier.address(addressing.forAccounts().of(bucket.getOwner()));
			if (bucket.getValidatorKey() != null) {
				if (bucket.resourceAddr() != null && bucket.getEpochUnlock() == null) {
					entityIdentifier.subEntity(new SubEntity()
						.address("prepared_stakes")
						.metadata(new SubEntityMetadata()
							.validator(addressing.forValidators().of(bucket.getValidatorKey()))
						)
					);
				} else if (bucket.resourceAddr() == null && Objects.equals(bucket.getEpochUnlock(), 0L)) {
					// Don't add validator as validator is already part of resource
					entityIdentifier.subEntity(new SubEntity()
						.address("prepared_unstakes")
					);
				} else if (bucket.resourceAddr() != null && bucket.getEpochUnlock() != null) {
					entityIdentifier.subEntity(new SubEntity()
						.address("exiting_unstakes")
						.metadata(new SubEntityMetadata()
							.validator(addressing.forValidators().of(bucket.getValidatorKey()))
							.epochUnlock(bucket.getEpochUnlock())
						)
					);
				}
			}
		} else if (substate instanceof ValidatorStakeData validatorStakeData) {
			entityIdentifier
				.address(addressing.forValidators().of(validatorStakeData.getValidatorKey()))
				.subEntity(new SubEntity().address("system"));
		} else if (substate instanceof ResourceData resourceData) {
			var symbol = addressToSymbol.apply(resourceData.getAddr());
			entityIdentifier.address(addressing.forResources().of(symbol, resourceData.getAddr()));
		} else if (substate instanceof SystemData) {
			entityIdentifier.address("system");
		} else if (substate instanceof ValidatorUpdatingData validatorUpdatingData) {
			entityIdentifier.address(addressing.forValidators().of(validatorUpdatingData.getValidatorKey()));
		} else if (substate instanceof com.radixdlt.application.validators.state.ValidatorData validatorData) {
			entityIdentifier
				.address(addressing.forValidators().of(validatorData.getValidatorKey()))
				.subEntity(new SubEntity().address("system"));
		} else if (substate instanceof UnclaimedREAddr unclaimedREAddr) {
			var addr = unclaimedREAddr.getAddr();
			final String address;
			if (addr.isSystem()) {
				address = "system";
			} else {
				var symbol = addressToSymbol.apply(addr);
				address = addressing.forResources().of(symbol, addr);
			}
			entityIdentifier.address(address);
		} else if (substate instanceof VirtualParent) {
			entityIdentifier.address("system");
		} else {
			throw new IllegalStateException();
		}

		return entityIdentifier;
	}

	public Optional<Data> data(Particle substate, boolean bootUp) {
		return dataObject(substate).map(dataObject -> new Data()
			.dataObject(dataObject)
			.action(bootUp ? Data.ActionEnum.CREATE : Data.ActionEnum.DELETE));
	}

	public Operation operation(REStateUpdate update, Function<REAddr, String> addressToSymbol) {
		var operation = new Operation();
		var substate = (Particle) update.getParsed();
		operation.type(SubstateTypeMapping.getType(SubstateTypeId.valueOf(update.typeByte())));
		operation.substate(substate(update.getId(), update.isBootUp()));
		/*
			.putOpt("metadata", update.isShutDown() ? null : new JSONObject()
				.put("substate_data_hex", Bytes.toHexString(update.getRawSubstateBytes().getData()))
			);
		 */
		operation.entityIdentifier(entityIdentifier(substate, addressToSymbol));
		if (substate instanceof ResourceInBucket resourceInBucket && !resourceInBucket.getAmount().isZero()) {
			operation.amount(resourceOperation(resourceInBucket, update.isBootUp(), addressToSymbol));
		}
		data(substate, update.isBootUp()).ifPresent(operation::data);
		return operation;
	}

	public OperationGroup operationGroup(List<REStateUpdate> stateUpdates, Function<REAddr, String> addressToSymbol) {
		var operationGroup = new OperationGroup();
		stateUpdates.forEach(u -> {
			operationGroup.addOperationsItem(operation(u, addressToSymbol));
		});
		return operationGroup;
	}

	public CommittedTransaction committedTransaction(
		REProcessedTxn txn,
		AccumulatorState accumulatorState,
		Function<REAddr, String> addressToSymbol
	) {
		Function<REAddr, String> localizedAddressToSymbol = addr -> {
			var localSymbol = txn.getGroupedStateUpdates().stream()
				.flatMap(List::stream)
				.map(REStateUpdate::getParsed)
				.filter(TokenResourceMetadata.class::isInstance)
				.map(TokenResourceMetadata.class::cast)
				.filter(r -> r.getAddr().equals(addr))
				.map(TokenResourceMetadata::getSymbol)
				.findFirst();

			return localSymbol.orElseGet(() -> addressToSymbol.apply(addr));
		};

		var transaction = new CommittedTransaction();

		for (var stateUpdates : txn.getGroupedStateUpdates()) {
			var operationGroup = operationGroup(stateUpdates, localizedAddressToSymbol);
			transaction.addOperationGroupsItem(operationGroup);
		}

		var metadata = new CommittedTransactionMetadata()
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
		Function<REAddr, String> localizedAddressToSymbol = addr -> {
			var localSymbol = txn.getGroupedStateUpdates().stream()
				.flatMap(List::stream)
				.map(REStateUpdate::getParsed)
				.filter(TokenResourceMetadata.class::isInstance)
				.map(TokenResourceMetadata.class::cast)
				.filter(r -> r.getAddr().equals(addr))
				.map(TokenResourceMetadata::getSymbol)
				.findFirst();

			return localSymbol.orElseGet(() -> addressToSymbol.apply(addr));
		};

		var transaction = new Transaction();

		for (var stateUpdates : txn.getGroupedStateUpdates()) {
			var operationGroup = operationGroup(stateUpdates, localizedAddressToSymbol);
			transaction.addOperationGroupsItem(operationGroup);
		}

		if (!txn.getFeePaid().isZero()) {
			transaction.metadata(new CommittedTransactionMetadata()
				.fee(nativeTokenAmount(txn.getFeePaid()))
			);
		}

		return transaction;
	}

	public TransactionIdentifier transactionIdentifier(AID txnId) {
		return new TransactionIdentifier()
			.hash(txnId.toString());
	}
}
