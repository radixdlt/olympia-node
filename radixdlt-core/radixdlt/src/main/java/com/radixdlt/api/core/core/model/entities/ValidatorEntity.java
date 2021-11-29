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

package com.radixdlt.api.core.core.model.entities;

import com.radixdlt.api.core.core.model.Entity;
import com.radixdlt.api.core.core.model.KeyQuery;
import com.radixdlt.api.core.core.model.ParsedDataObject;
import com.radixdlt.api.core.core.model.Resource;
import com.radixdlt.api.core.core.model.ResourceQuery;
import com.radixdlt.api.core.core.model.ResourceUnsignedAmount;
import com.radixdlt.api.core.core.model.SubstateWithdrawal;
import com.radixdlt.api.core.core.model.exceptions.RawCoreTxBuilderException;
import com.radixdlt.api.core.core.model.exceptions.EntityDoesNotSupportOperationException;
import com.radixdlt.api.core.core.model.exceptions.OverValidatorFeeIncreaseLimitException;
import com.radixdlt.api.core.core.openapitools.model.PreparedValidatorFee;
import com.radixdlt.api.core.core.openapitools.model.PreparedValidatorOwner;
import com.radixdlt.api.core.core.openapitools.model.PreparedValidatorRegistered;
import com.radixdlt.api.core.core.openapitools.model.ValidatorAllowDelegation;
import com.radixdlt.api.core.core.openapitools.model.ValidatorMetadata;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.Bytes;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static com.radixdlt.atom.SubstateTypeId.*;

public final class ValidatorEntity implements Entity {
	private final ECPublicKey validatorKey;

	private ValidatorEntity(ECPublicKey validatorKey) {
		this.validatorKey = validatorKey;
	}

	public static ValidatorEntity from(ECPublicKey validatorKey) {
		return new ValidatorEntity(validatorKey);
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	@Override
	public void deposit(ResourceUnsignedAmount amount, TxBuilder txBuilder, Supplier<RERulesConfig> config)
		throws RawCoreTxBuilderException {
		throw new EntityDoesNotSupportOperationException("Cannot deposit to Validator Entity");
	}

	@Override
	public SubstateWithdrawal withdraw(Resource resource) throws RawCoreTxBuilderException {
		throw new EntityDoesNotSupportOperationException("Cannot withdraw from Validator Entity");
	}

	@Override
	public void overwriteDataObject(
		ParsedDataObject parsedDataObject,
		TxBuilder builder,
		Supplier<RERulesConfig> config
	) throws RawCoreTxBuilderException {
		var dataObject = parsedDataObject.getDataObject();
		if (dataObject instanceof PreparedValidatorRegistered preparedValidatorRegistered) {
			builder.down(ValidatorRegisteredCopy.class, validatorKey);
			var curEpoch = builder.readSystem(EpochData.class);
			builder.up(new ValidatorRegisteredCopy(
				OptionalLong.of(curEpoch.getEpoch() + 1),
				validatorKey,
				preparedValidatorRegistered.getRegistered()
			));
		} else if (dataObject instanceof PreparedValidatorOwner) {
			builder.down(ValidatorOwnerCopy.class, validatorKey);
			var curEpoch = builder.readSystem(EpochData.class);
			var owner = parsedDataObject.getParsed(REAddr.class);
			builder.up(new ValidatorOwnerCopy(OptionalLong.of(curEpoch.getEpoch() + 1), validatorKey, owner));
		} else if (dataObject instanceof PreparedValidatorFee preparedValidatorFee) {
			builder.down(ValidatorFeeCopy.class, validatorKey);
			var curRakePercentage = builder.read(ValidatorStakeData.class, validatorKey)
				.getRakePercentage();
			int validatorFee = preparedValidatorFee.getFee();
			var isIncrease = validatorFee > curRakePercentage;
			var rakeIncrease = validatorFee - curRakePercentage;
			var maxRakeIncrease = ValidatorUpdateRakeConstraintScrypt.MAX_RAKE_INCREASE;
			if (isIncrease && rakeIncrease >= maxRakeIncrease) {
				throw new OverValidatorFeeIncreaseLimitException(maxRakeIncrease, rakeIncrease);
			}

			var rakeIncreaseDebounceEpochLength = config.get().getRakeIncreaseDebouncerEpochLength();
			var epochDiff = isIncrease ? (1 + rakeIncreaseDebounceEpochLength) : 1;
			var curEpoch = builder.readSystem(EpochData.class);
			var epoch = curEpoch.getEpoch() + epochDiff;
			builder.up(new ValidatorFeeCopy(OptionalLong.of(epoch), validatorKey, validatorFee));
		} else if (dataObject instanceof ValidatorMetadata metadata) {
			var substateDown = builder.down(ValidatorMetaData.class, validatorKey);
			builder.up(new ValidatorMetaData(
				validatorKey,
				metadata.getName() == null ? substateDown.getName() : metadata.getName(),
				metadata.getUrl() == null ? substateDown.getUrl() : metadata.getUrl()
			));
		} else if (dataObject instanceof ValidatorAllowDelegation allowDelegation) {
			builder.down(AllowDelegationFlag.class, validatorKey);
			builder.up(new AllowDelegationFlag(validatorKey, allowDelegation.getAllowDelegation()));
		} else if (dataObject instanceof com.radixdlt.api.core.core.openapitools.model.ValidatorSystemMetadata metadata) {
			builder.down(com.radixdlt.application.validators.state.ValidatorSystemMetadata.class, validatorKey);
			builder.up(new com.radixdlt.application.validators.state.ValidatorSystemMetadata(
				validatorKey,
				Bytes.fromHexString(metadata.getData())
			));
		} else {
			throw new EntityDoesNotSupportOperationException("Entity does not support data object " + dataObject);
		}
	}

	@Override
	public List<ResourceQuery> getResourceQueries() {
		var index = SubstateIndex.<ResourceInBucket>create(VALIDATOR_STAKE_DATA.id(), ValidatorStakeData.class);
		return List.of(ResourceQuery.from(index, b -> b.bucket().getValidatorKey().equals(validatorKey)));
	}

	@Override
	public List<KeyQuery> getKeyQueries() {
		return List.of(
			KeyQuery.fromValidator(validatorKey, VALIDATOR_META_DATA, ValidatorMetaData::createVirtual),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_STAKE_DATA, ValidatorStakeData::createVirtual),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_BFT_DATA),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_ALLOW_DELEGATION_FLAG, AllowDelegationFlag::createVirtual),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_REGISTERED_FLAG_COPY, ValidatorRegisteredCopy::createVirtual),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_RAKE_COPY, ValidatorFeeCopy::createVirtual),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_OWNER_COPY, ValidatorOwnerCopy::createVirtual),
			KeyQuery.fromValidator(validatorKey, VALIDATOR_SYSTEM_META_DATA, ValidatorSystemMetadata::createVirtual)
		);
	}
}
