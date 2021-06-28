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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.DeprecatedUnstakeTokens;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.PayFee;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SplitToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.atom.actions.UpdateRake;
import com.radixdlt.atom.actions.UpdateValidatorMetadata;
import com.radixdlt.atom.actions.UpdateValidatorOwnerAddress;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV3;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV3;
import com.radixdlt.atommodel.system.construction.PayFeeConstructorV2;
import com.radixdlt.atommodel.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.atommodel.system.scrypt.FeeConstraintScrypt;
import com.radixdlt.atommodel.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.construction.BurnTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.CreateFixedTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.DeprecatedUnstakeTokensConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.SplitTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.UnstakeOwnershipConstructor;
import com.radixdlt.atommodel.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.atommodel.unique.scrypt.MutexConstraintScrypt;
import com.radixdlt.atommodel.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.atommodel.validators.construction.UnregisterValidatorConstructor;
import com.radixdlt.atommodel.validators.construction.UpdateAllowDelegationFlagConstructor;
import com.radixdlt.atommodel.validators.construction.UpdateRakeConstructor;
import com.radixdlt.atommodel.validators.construction.UpdateValidatorConstructor;
import com.radixdlt.atommodel.validators.construction.UpdateValidatorOwnerConstructor;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.atommodel.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.metering.FixedFeeMetering;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.EpochProofVerifierV2;
import com.radixdlt.utils.UInt256;

import java.util.Set;
import java.util.function.Function;

public final class MainnetForkRulesModule extends AbstractModule {
	public static final UInt256 FIXED_FEE = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(100));

	@ProvidesIntoMap
	@StringMapKey("mainnet")
	Function<RERulesConfig, RERules> mainnet() {
		return config -> {
			var maxRounds = config.getMaxRounds();
			var fees = config.includeFees();
			var rakeIncreaseDebouncerEpochLength = config.getRakeIncreaseDebouncerEpochLength();

			final CMAtomOS v4 = new CMAtomOS(Set.of(TokenDefinitionUtils.getNativeTokenShortCode()));
			v4.load(new ValidatorConstraintScryptV2(rakeIncreaseDebouncerEpochLength));
			v4.load(new ValidatorRegisterConstraintScrypt());
			v4.load(new TokensConstraintScryptV3());
			v4.load(new FeeConstraintScrypt());
			v4.load(new StakingConstraintScryptV4());
			v4.load(new MutexConstraintScrypt());
			v4.load(new RoundUpdateConstraintScrypt(maxRounds));
			v4.load(new EpochUpdateConstraintScrypt(maxRounds));
			var betanet4 = new ConstraintMachineConfig(
				v4.virtualizedUpParticles(),
				v4.getProcedures(),
				fees ? new FixedFeeMetering(FIXED_FEE) : (procedureKey, param, context) -> { }
			);
			var parser = new REParser(v4.buildSubstateDeserialization());
			var serialization = v4.buildSubstateSerialization();
			var actionConstructors = ActionConstructors.newBuilder()
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(BurnToken.class, new BurnTokenConstructor())
				.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(DeprecatedUnstakeTokens.class, new DeprecatedUnstakeTokensConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(NextEpoch.class, new NextEpochConstructorV3())
				.put(NextRound.class, new NextViewConstructorV3())
				.put(RegisterValidator.class, new RegisterValidatorConstructor())
				.put(SplitToken.class, new SplitTokenConstructor())
				.put(StakeTokens.class, new StakeTokensConstructorV3())
				.put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
				.put(UnstakeOwnership.class, new UnstakeOwnershipConstructor())
				.put(TransferToken.class, new TransferTokensConstructorV2())
				.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
				.put(UpdateValidatorMetadata.class, new UpdateValidatorConstructor())
				.put(PayFee.class, new PayFeeConstructorV2())
				.put(UpdateRake.class, new UpdateRakeConstructor(rakeIncreaseDebouncerEpochLength))
				.put(UpdateValidatorOwnerAddress.class, new UpdateValidatorOwnerConstructor())
				.put(UpdateAllowDelegationFlag.class, new UpdateAllowDelegationFlagConstructor())
				.build();

			return new RERules(
				"mainnet",
				parser,
				serialization,
				betanet4,
				actionConstructors,
				new EpochProofVerifierV2(),
				View.of(maxRounds)
			);
		};
	}
}
