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
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateValidator;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV1;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV2;
import com.radixdlt.atommodel.system.construction.PayFeeConstructorV1;
import com.radixdlt.atommodel.system.construction.PayFeeConstructorV2;
import com.radixdlt.atommodel.system.scrypt.FeeConstraintScrypt;
import com.radixdlt.atommodel.system.scrypt.SystemV1ToV2TransitionConstraintScrypt;
import com.radixdlt.atommodel.tokens.construction.BurnTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.CreateFixedTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.DeprecatedUnstakeTokensConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV1;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV1;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.UnstakeOwnershipConstructor;
import com.radixdlt.atommodel.tokens.construction.UnstakeTokensConstructorV1;
import com.radixdlt.atommodel.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV2;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.atommodel.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.atommodel.tokens.construction.SplitTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV1;
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructorV1;
import com.radixdlt.atommodel.validators.construction.UnregisterValidatorConstructor;
import com.radixdlt.atommodel.validators.construction.UpdateValidatorConstructor;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV1;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV1;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV2;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV1;
import com.radixdlt.atommodel.unique.scrypt.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.metering.FixedFeeMetering;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.EpochProofVerifierV1;
import com.radixdlt.statecomputer.EpochProofVerifierV2;
import com.radixdlt.statecomputer.transaction.TokenFeeChecker;
import com.radixdlt.utils.UInt256;

import java.util.Set;

/**
 * The forks for betanet and the epochs at which they will occur.
 */
public final class BetanetForksModule extends AbstractModule {
	@ProvidesIntoMap
	@EpochMapKey(epoch = 0L)
	ForkConfig betanetV1() {
		// V1 Betanet ConstraintMachine
		final CMAtomOS v1 = new CMAtomOS(Set.of(TokenDefinitionUtils.getNativeTokenShortCode()));
		v1.load(new ValidatorConstraintScrypt()); // load before TokensConstraintScrypt due to dependency
		v1.load(new TokensConstraintScryptV1());
		v1.load(new StakingConstraintScryptV1());
		v1.load(new UniqueParticleConstraintScrypt());
		v1.load(new SystemConstraintScryptV1());
		var betanet1 = new ConstraintMachineConfig(
			v1.virtualizedUpParticles(),
			v1.getProcedures(),
			(procedureKey, param, context) -> { }
		);
		var parser = new REParser(v1.buildSubstateDeserialization());
		var serialization = v1.buildSubstateSerialization();
		var actionConstructors = ActionConstructors.newBuilder()
			.put(CreateSystem.class, new CreateSystemConstructorV1())
			.put(PayFee.class, new PayFeeConstructorV1())
			.put(BurnToken.class, new BurnTokenConstructor())
			.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(DeprecatedUnstakeTokens.class, new DeprecatedUnstakeTokensConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(SystemNextEpoch.class, new NextEpochConstructorV1())
			.put(SystemNextView.class, new NextViewConstructorV1())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(SplitToken.class, new SplitTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructorV1())
			.put(TransferToken.class, new TransferTokensConstructorV1())
			.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
			.put(UnstakeTokens.class, new UnstakeTokensConstructorV1())
			.put(UpdateValidator.class, new UpdateValidatorConstructor())
			.build();

		return new ForkConfig(
			"betanet1",
			parser,
			serialization,
			betanet1,
			actionConstructors,
			new EpochProofVerifierV1(),
			new TokenFeeChecker(),
			//View.of(100000L)
			View.of(1000L)
		);
	}

	@ProvidesIntoMap
	//@EpochMapKey(epoch = 45L)
	@EpochMapKey(epoch = 4L)
	ForkConfig betanetV2() {
		// V2 Betanet ConstraintMachine
		final CMAtomOS v2 = new CMAtomOS(Set.of(TokenDefinitionUtils.getNativeTokenShortCode()));
		v2.load(new ValidatorConstraintScrypt()); // load before TokensConstraintScrypt due to dependency
		v2.load(new TokensConstraintScryptV1());
		v2.load(new StakingConstraintScryptV2());
		v2.load(new UniqueParticleConstraintScrypt());
		v2.load(new SystemConstraintScryptV1());
		var betanet2 = new ConstraintMachineConfig(
			v2.virtualizedUpParticles(),
			v2.getProcedures(),
			(procedureKey, param, context) -> { }
		);

		var parser = new REParser(v2.buildSubstateDeserialization());
		var serialization = v2.buildSubstateSerialization();
		var actionConstructors = ActionConstructors.newBuilder()
			.put(CreateSystem.class, new CreateSystemConstructorV1())
			.put(PayFee.class, new PayFeeConstructorV1())
			.put(BurnToken.class, new BurnTokenConstructor())
			.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(DeprecatedUnstakeTokens.class, new DeprecatedUnstakeTokensConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(SystemNextEpoch.class, new NextEpochConstructorV1())
			.put(SystemNextView.class, new NextViewConstructorV1())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(SplitToken.class, new SplitTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructorV1())
			.put(TransferToken.class, new TransferTokensConstructorV1())
			.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
			.put(UnstakeTokens.class, new UnstakeTokensConstructorV1())
			.put(UpdateValidator.class, new UpdateValidatorConstructor())
			.build();

		return new ForkConfig(
			"betanet2",
			parser,
			serialization,
			betanet2,
			actionConstructors,
			new EpochProofVerifierV1(),
			new TokenFeeChecker(),
			//View.of(10000L)
			View.of(1000L)
		);
	}

	@ProvidesIntoMap
	@EpochMapKey(epoch = 8L)
	ForkConfig betanetV3() {
		final CMAtomOS v3 = new CMAtomOS(Set.of(TokenDefinitionUtils.getNativeTokenShortCode()));
		v3.load(new ValidatorConstraintScrypt()); // load before TokensConstraintScrypt due to dependency
		v3.load(new TokensConstraintScryptV2());
		v3.load(new StakingConstraintScryptV3());
		v3.load(new UniqueParticleConstraintScrypt());
		v3.load(new SystemConstraintScryptV2());
		v3.load(new SystemV1ToV2TransitionConstraintScrypt());
		var betanet3 = new ConstraintMachineConfig(
			v3.virtualizedUpParticles(),
			v3.getProcedures(),
			(procedureKey, param, context) -> { }
		);

		var parser = new REParser(v3.buildSubstateDeserialization());
		var serialization = v3.buildSubstateSerialization();
		var actionConstructors = ActionConstructors.newBuilder()
			.put(CreateSystem.class, new CreateSystemConstructorV2())
			.put(PayFee.class, new PayFeeConstructorV1())
			.put(BurnToken.class, new BurnTokenConstructor())
			.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(DeprecatedUnstakeTokens.class, new DeprecatedUnstakeTokensConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(SystemNextEpoch.class, new NextEpochConstructorV2())
			.put(SystemNextView.class, new NextViewConstructorV2())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(SplitToken.class, new SplitTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructorV2())
			.put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
			.put(UnstakeOwnership.class, new UnstakeOwnershipConstructor())
			.put(TransferToken.class, new TransferTokensConstructorV2())
			.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
			.put(UpdateValidator.class, new UpdateValidatorConstructor())
			.build();

		return new ForkConfig(
			"betanet3",
			parser,
			serialization,
			betanet3,
			actionConstructors,
			new EpochProofVerifierV2(),
			new TokenFeeChecker(),
			View.of(1000L)
		);
	}


	@ProvidesIntoMap
	@EpochMapKey(epoch = 10L)
	ForkConfig betanetV4() {
		var fixedFee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(100));
		final CMAtomOS v4 = new CMAtomOS(Set.of(TokenDefinitionUtils.getNativeTokenShortCode()));
		v4.load(new ValidatorConstraintScrypt()); // load before TokensConstraintScrypt due to dependency
		v4.load(new TokensConstraintScryptV3());
		v4.load(new FeeConstraintScrypt());
		v4.load(new StakingConstraintScryptV3());
		v4.load(new UniqueParticleConstraintScrypt());
		v4.load(new SystemConstraintScryptV2());
		v4.load(new SystemV1ToV2TransitionConstraintScrypt());
		var betanet4 = new ConstraintMachineConfig(
			v4.virtualizedUpParticles(),
			v4.getProcedures(),
			new FixedFeeMetering(fixedFee)
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
			.put(SystemNextEpoch.class, new NextEpochConstructorV2())
			.put(SystemNextView.class, new NextViewConstructorV2())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(SplitToken.class, new SplitTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructorV2())
			.put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
			.put(UnstakeOwnership.class, new UnstakeOwnershipConstructor())
			.put(TransferToken.class, new TransferTokensConstructorV2())
			.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
			.put(UpdateValidator.class, new UpdateValidatorConstructor())
			.put(PayFee.class, new PayFeeConstructorV2())
			.build();

		return new ForkConfig(
			"betanet4",
			parser,
			serialization,
			betanet4,
			actionConstructors,
			new EpochProofVerifierV2(),
			(p, txn) -> { },
			View.of(10000L)
		);
	}
}
