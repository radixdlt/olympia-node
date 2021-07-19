/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.statecomputer.forks;

import com.radixdlt.application.misc.FaucetTokensTransferConstructor;
import com.radixdlt.application.system.construction.FeeReserveCompleteConstructor;
import com.radixdlt.application.validators.construction.UpdateValidatorSystemMetadataConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateOwnerConstraintScrypt;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.FaucetTokensTransfer;
import com.radixdlt.atom.actions.FeeReserveComplete;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SplitToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.atom.actions.UpdateValidatorFee;
import com.radixdlt.atom.actions.UpdateValidatorMetadata;
import com.radixdlt.atom.actions.UpdateValidatorOwner;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.NextEpochConstructorV3;
import com.radixdlt.application.system.construction.NextViewConstructorV3;
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.tokens.construction.BurnTokenConstructor;
import com.radixdlt.application.tokens.construction.CreateFixedTokenConstructor;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.misc.SplitTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.construction.UnstakeOwnershipConstructor;
import com.radixdlt.application.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.unique.scrypt.MutexConstraintScrypt;
import com.radixdlt.application.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.application.validators.construction.UnregisterValidatorConstructor;
import com.radixdlt.application.validators.construction.UpdateAllowDelegationFlagConstructor;
import com.radixdlt.application.validators.construction.UpdateRakeConstructor;
import com.radixdlt.application.validators.construction.UpdateValidatorMetadataConstructor;
import com.radixdlt.application.validators.construction.UpdateValidatorOwnerConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.atom.actions.UpdateValidatorSystemMetadata;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.meter.Meter;
import com.radixdlt.constraintmachine.meter.Meters;
import com.radixdlt.constraintmachine.meter.UpSubstateFeeMeter;
import com.radixdlt.constraintmachine.meter.SigsPerRoundMeter;
import com.radixdlt.constraintmachine.meter.TxnSizeFeeMeter;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.EpochProofVerifierV2;

public enum RERulesVersion {
	OLYMPIA_V1 {
		@Override
		public RERules create(RERulesConfig config) {
			var maxRounds = config.getMaxRounds();
			var perByteFee = config.getFeeTable().getPerByteFee();
			var perUpSubstateFee = config.getFeeTable().getPerUpSubstateFee();
			var rakeIncreaseDebouncerEpochLength = config.getRakeIncreaseDebouncerEpochLength();

			final CMAtomOS v4 = new CMAtomOS();
			v4.load(new ValidatorConstraintScryptV2());
			v4.load(new ValidatorUpdateRakeConstraintScrypt(rakeIncreaseDebouncerEpochLength));
			v4.load(new ValidatorRegisterConstraintScrypt());
			v4.load(new ValidatorUpdateOwnerConstraintScrypt());
			v4.load(new SystemConstraintScrypt());
			v4.load(new TokensConstraintScryptV3(config.getReservedSymbols()));
			v4.load(new StakingConstraintScryptV4(config.getMinimumStake().toSubunits()));
			v4.load(new MutexConstraintScrypt());
			v4.load(new RoundUpdateConstraintScrypt(maxRounds));
			v4.load(new EpochUpdateConstraintScrypt(
				maxRounds,
				config.getRewardsPerProposal().toSubunits(),
				config.getMinimumCompletedProposalsPercentage(),
				config.getUnstakingEpochDelay(),
				config.getMaxValidators()
			));
			var meter = Meters.combine(
				config.getMaxSigsPerRound().stream().<Meter>mapToObj(SigsPerRoundMeter::create).findAny().orElse(Meter.EMPTY),
				Meters.combine(
					TxnSizeFeeMeter.create(perByteFee),
					UpSubstateFeeMeter.create(perUpSubstateFee)
				)
			);
			var betanet4 = new ConstraintMachineConfig(
				v4.getProcedures(),
				v4.buildSubstateDeserialization(),
				v4.buildVirtualSubstateDeserialization(),
				meter
			);
			var parser = new REParser(v4.buildSubstateDeserialization());
			var serialization = v4.buildSubstateSerialization();
			var actionConstructors = REConstructor.newBuilder()
				.perByteFee(perByteFee)
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(BurnToken.class, new BurnTokenConstructor())
				.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(NextEpoch.class, new NextEpochConstructorV3(
					config.getRewardsPerProposal().toSubunits(),
					config.getMinimumCompletedProposalsPercentage(),
					config.getUnstakingEpochDelay(),
					config.getMaxValidators()
				))
				.put(NextRound.class, new NextViewConstructorV3())
				.put(RegisterValidator.class, new RegisterValidatorConstructor())
				.put(FaucetTokensTransfer.class, new FaucetTokensTransferConstructor())
				.put(SplitToken.class, new SplitTokenConstructor())
				.put(StakeTokens.class, new StakeTokensConstructorV3(config.getMinimumStake().toSubunits()))
				.put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
				.put(UnstakeOwnership.class, new UnstakeOwnershipConstructor())
				.put(TransferToken.class, new TransferTokensConstructorV2())
				.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
				.put(UpdateValidatorMetadata.class, new UpdateValidatorMetadataConstructor())
				.put(FeeReservePut.class, new FeeReservePutConstructor())
				.put(FeeReserveComplete.class, new FeeReserveCompleteConstructor(config.getFeeTable()))
				.put(UpdateValidatorFee.class, new UpdateRakeConstructor(
					rakeIncreaseDebouncerEpochLength,
					ValidatorUpdateRakeConstraintScrypt.MAX_RAKE_INCREASE
				))
				.put(UpdateValidatorOwner.class, new UpdateValidatorOwnerConstructor())
				.put(UpdateAllowDelegationFlag.class, new UpdateAllowDelegationFlagConstructor())
				.put(UpdateValidatorSystemMetadata.class, new UpdateValidatorSystemMetadataConstructor())
				.build();

			return new RERules(
				"mainnet",
				parser,
				serialization,
				betanet4,
				actionConstructors,
				new EpochProofVerifierV2(),
				config
			);
		}
	};


	public abstract RERules create(RERulesConfig config);
}
