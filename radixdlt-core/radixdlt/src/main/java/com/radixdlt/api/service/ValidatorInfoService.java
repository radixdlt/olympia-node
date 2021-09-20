/*
 *  Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *  *
 *  * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 *  * file except in compliance with the License. You may obtain a copy of the License at:
 *  *
 *  * radixfoundation.org/licenses/LICENSE-v1
 *  *
 *  * The Licensor hereby grants permission for the Canonical version of the Work to be
 *  * published, distributed and used under or by reference to the Licensor’s trademark
 *  * Radix ® and use of any unregistered trade names, logos or get-up.
 *  *
 *  * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 *  * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 *  * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *  *
 *  * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 *  * a distributed ledger it is your responsibility to test and validate the code, together
 *  * with all logic and performance of that code under all foreseeable scenarios.
 *  *
 *  * The Licensor does not make or purport to make and hereby excludes liability for all
 *  * and any representation, warranty or undertaking in any form whatsoever, whether express
 *  * or implied, to any entity or person, including any representation, warranty or
 *  * undertaking, as to the functionality security use, value or other characteristics of
 *  * any distributed ledger nor in respect the functioning or value of any tokens which may
 *  * be created stored or transferred using the Work. The Licensor does not warrant that the
 *  * Work or any use of the Work complies with any law or regulation in any territory where
 *  * it may be implemented or used or that it will be appropriate for any specific purpose.
 *  *
 *  * Neither the licensor nor any current or former employees, officers, directors, partners,
 *  * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 *  * shall be liable for any direct or indirect, special, incidental, consequential or other
 *  * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 *  * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 *  * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 *  * out of or in connection with (without limitation of any use, misuse, of any ledger system
 *  * or use made or its functionality or any performance or operation of any code or protocol
 *  * caused by bugs or programming or logic errors or otherwise);
 *  *
 *  * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 *  * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 *  * interaction with the Work;
 *  *
 *  * B. any failure in a transmission or loss of any token or assets keys or other digital
 *  * artefacts due to errors in transmission;
 *  *
 *  * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *  *
 *  * D. system software or apparatus including but not limited to losses caused by errors
 *  * in holding or transmitting tokens by any third-party;
 *  *
 *  * E. breaches or failure of security including hacker attacks, loss or disclosure of
 *  * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *  *
 *  * F. any losses including loss of anticipated savings or other benefits resulting from
 *  * use of the Work or any changes to the Work (however implemented).
 *  *
 *  * You are solely responsible for; testing, validating and evaluation of all operation
 *  * logic, functionality, security and appropriateness of using the Work for any commercial
 *  * or non-commercial purpose and for any reproduction or redistribution by You of the
 *  * Work. You assume all risks associated with Your use of the Work and the exercise of
 *  * permissions under this License.
 *
 */

package com.radixdlt.api.service;

import org.bouncycastle.util.Arrays;

import com.google.inject.Inject;
import com.radixdlt.api.data.ValidatorUptime;
import com.radixdlt.api.functional.FunctionalRadixEngine;
import com.radixdlt.api.util.StakeUtils;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.util.Map;

import static com.radixdlt.atom.SubstateTypeId.PREPARED_STAKE;
import static com.radixdlt.atom.SubstateTypeId.PREPARED_UNSTAKE;
import static com.radixdlt.atom.SubstateTypeId.STAKE_OWNERSHIP;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_BFT_DATA;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_META_DATA;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_OWNER_COPY;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_RAKE_COPY;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_STAKE_DATA;

public final class ValidatorInfoService {
	private final FunctionalRadixEngine radixEngine;

	@Inject
	public ValidatorInfoService(FunctionalRadixEngine radixEngine) {
		this.radixEngine = radixEngine;
	}

	public Result<ValidatorMetaData> getMetadata(ECPublicKey validatorKey) {
		var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_META_DATA.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorDataKey)
			.map(optional -> (ValidatorMetaData) optional.orElse(ValidatorMetaData.createVirtual(validatorKey)));
	}

	public Result<AllowDelegationFlag> getAllowDelegationFlag(ECPublicKey validatorKey) {
		var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_ALLOW_DELEGATION_FLAG.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorDataKey)
			.map(optional -> (AllowDelegationFlag) optional.orElse(AllowDelegationFlag.createVirtual(validatorKey)));
	}

	public Result<ValidatorUptime> getUptime(ECPublicKey validatorKey) {
		var validatorUptimeKey = SystemMapKey.ofSystem(VALIDATOR_BFT_DATA.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorUptimeKey)
			.map(optional -> optional.map(ValidatorBFTData.class::cast))
			.map(optional -> optional.map(d -> ValidatorUptime.create(d.proposalsCompleted(), d.proposalsMissed())))
			.map(optional -> optional.orElse(ValidatorUptime.empty()));
	}

	public Result<ValidatorRegisteredCopy> getNextEpochRegisteredFlag(ECPublicKey validatorKey) {
		var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_REGISTERED_FLAG_COPY.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorDataKey)
			.map(optional -> (ValidatorRegisteredCopy) optional.orElse(ValidatorRegisteredCopy.createVirtual(validatorKey)));
	}

	public Result<ValidatorFeeCopy> getNextValidatorFee(ECPublicKey validatorKey) {
		var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_RAKE_COPY.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorDataKey)
			.map(optional -> (ValidatorFeeCopy) optional.orElse(ValidatorFeeCopy.createVirtual(validatorKey)));
	}

	public Result<ValidatorOwnerCopy> getNextEpochValidatorOwner(ECPublicKey validatorKey) {
		var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_OWNER_COPY.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorDataKey)
			.map(optional -> (ValidatorOwnerCopy) optional.orElse(ValidatorOwnerCopy.createVirtual(validatorKey)));
	}


	public Result<Map<REAddr, UInt384>> getPreparedStakes(ECPublicKey validatorKey) {
		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[]{PREPARED_STAKE.id(), 0}, validatorKey.getCompressedBytes()),
			PreparedStake.class
		);

		return radixEngine.reduceResources(index, PreparedStake::getOwner);
	}

	public Result<Map<REAddr, UInt384>> getPreparedOwnershipUnstakes(ECPublicKey validatorKey) {
		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[]{PREPARED_UNSTAKE.id(), 0}, validatorKey.getCompressedBytes()),
			PreparedUnstakeOwnership.class
		);

		return radixEngine.reduceResources(index, PreparedUnstakeOwnership::getOwner);
	}

	public Result<Map<REAddr, UInt256>> getEstimatedPreparedUnstakes(ValidatorStakeData curData) {
		var validatorKey = curData.getValidatorKey();

		return getPreparedOwnershipUnstakes(validatorKey)
			.map(ownershipUnstakes -> StakeUtils.toAmountPerAddress(curData, ownershipUnstakes));
	}

	public Result<UInt256> getEstimatedIndividualStake(ValidatorStakeData curData, REAddr accountAddr) {
		var validatorKey = curData.getValidatorKey();

		var index = SubstateIndex.create(
			Arrays.concatenate(
				new byte[]{STAKE_OWNERSHIP.id(), 0},
				validatorKey.getCompressedBytes(),
				accountAddr.getBytes()
			),
			StakeOwnership.class
		);

		return radixEngine.reduceResources(index).map(stake -> stake.isZero()
															   ? UInt256.ZERO
															   : StakeUtils.toAmount(curData, stake));
	}

	public Result<Map<REAddr, UInt256>> getEstimatedIndividualStakes(ValidatorStakeData curData) {
		var validatorKey = curData.getValidatorKey();
		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[]{STAKE_OWNERSHIP.id(), 0}, validatorKey.getCompressedBytes()),
			StakeOwnership.class
		);

		return radixEngine.reduceResources(index, StakeOwnership::getOwner)
			.map(stakeReceived -> StakeUtils.toAmountPerAddress(curData, stakeReceived));
	}

	public Result<ValidatorStakeData> getValidatorStakeData(ECPublicKey validatorKey) {
		var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_STAKE_DATA.id(), validatorKey.getCompressedBytes());

		return radixEngine.getParticle(validatorDataKey)
			.map(optional -> (ValidatorStakeData) optional.orElse(ValidatorStakeData.createVirtual(validatorKey)));
	}
}
