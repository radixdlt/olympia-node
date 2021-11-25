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

import com.google.inject.Inject;
import com.radixdlt.api.core.core.construction.Entity;
import com.radixdlt.api.core.core.construction.entities.AccountVaultEntityIdentifier;
import com.radixdlt.api.core.core.construction.entities.ValidatorEntityIdentifier;
import com.radixdlt.api.core.core.openapitools.model.*;
import com.radixdlt.api.service.transactions.SubstateTypeMapping;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.function.Function;

public class ModelMapper {
	private final Addressing addressing;

	@Inject
	ModelMapper(Addressing addressing) {
		this.addressing = addressing;
	}

	public Entity entity(EntityIdentifier entityIdentifier) throws Exception {
		var address = entityIdentifier.getAddress();
		var addressType = addressing.getAddressType(address).orElseThrow();
		switch (addressType) {
			case VALIDATOR -> {
				var key = addressing.forValidators().parse(address);
				return ValidatorEntityIdentifier.from(key);
			}
			case ACCOUNT -> {
				var accountAddress = addressing.forAccounts().parse(address);
				return AccountVaultEntityIdentifier.from(accountAddress);
			}
			default -> throw new IllegalStateException();
		}
	}

	public StateIdentifier stateIdentifier(AccumulatorState accumulatorState) {
		return new StateIdentifier()
			.stateVersion(accumulatorState.getStateVersion())
			.transactionAccumulator(Bytes.toHexString(accumulatorState.getAccumulatorHash().asBytes()));
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

	public ResourceAmount resourceAmount(Bucket bucket, UInt384 amount, Function<REAddr, String> tokenAddressToSymbol) {
		return new ResourceAmount()
			.resourceIdentifier(resourceIdentifier(bucket, tokenAddressToSymbol))
			.value(amount.toString());
	}

	public ResourceAmount nativeTokenAmount(UInt256 amount) {
		return new ResourceAmount()
			.resourceIdentifier(nativeToken())
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

	public DataObject dataObject(Particle substate) {
		if (substate instanceof TokenResource tokenResource) {
			return tokenData(tokenResource);
		} else if (substate instanceof TokenResourceMetadata metadata) {
			return tokenMetadata(metadata);
		} else if (substate instanceof EpochData epochData) {
			return epochData(epochData);
		} else if (substate instanceof RoundData roundData) {
			return roundData(roundData);
		} else if (substate instanceof ValidatorRegisteredCopy validatorRegisteredCopy) {
			return preparedValidatorRegistered(validatorRegisteredCopy);
		} else if (substate instanceof ValidatorOwnerCopy validatorOwnerCopy) {
			return preparedValidatorOwner(validatorOwnerCopy);
		} else if (substate instanceof ValidatorFeeCopy validatorFeeCopy) {
			return preparedValidatorFee(validatorFeeCopy);
		} else if (substate instanceof ValidatorMetaData validatorMetaData) {
			return validatorMetadata(validatorMetaData);
		} else if (substate instanceof ValidatorBFTData validatorBFTData) {
			return validatorBFTData(validatorBFTData);
		} else if (substate instanceof AllowDelegationFlag allowDelegationFlag) {
			return allowDelegationFlag(allowDelegationFlag);
		} else if (substate instanceof ValidatorSystemMetadata validatorSystemMetadata) {
			return validatorSystemMetadata(validatorSystemMetadata);
		} else if (substate instanceof ValidatorStakeData validatorStakeData) {
			return validatorStakeData(validatorStakeData);
		} else {
			throw new IllegalStateException("Unknown data object:  " + substate);
		}
	}
}
