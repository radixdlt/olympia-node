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
import com.radixdlt.atom.actions.DeprecatedUnstakeTokens;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SplitToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateValidator;
import com.radixdlt.atom.construction.BurnTokenConstructor;
import com.radixdlt.atom.construction.CreateFixedTokenConstructor;
import com.radixdlt.atom.construction.CreateMutableTokenConstructor;
import com.radixdlt.atom.construction.DeprecatedUnstakeTokensConstructor;
import com.radixdlt.atom.construction.MintTokenConstructor;
import com.radixdlt.atom.construction.NextEpochConstructor;
import com.radixdlt.atom.construction.NextViewConstructor;
import com.radixdlt.atom.construction.RegisterValidatorConstructor;
import com.radixdlt.atom.construction.SplitTokenConstructor;
import com.radixdlt.atom.construction.StakeTokensConstructor;
import com.radixdlt.atom.construction.TransferTokensConstructor;
import com.radixdlt.atom.construction.UnregisterValidatorConstructor;
import com.radixdlt.atom.construction.UnstakeTokensConstructor;
import com.radixdlt.atom.construction.UpdateValidatorConstructor;
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atommodel.tokens.StakingConstraintScryptV1;
import com.radixdlt.atommodel.tokens.StakingConstraintScryptV2;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;

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
		v1.load(new TokensConstraintScrypt());
		v1.load(new StakingConstraintScryptV1());
		v1.load(new UniqueParticleConstraintScrypt());
		v1.load(new SystemConstraintScrypt());
		var betanet1 = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(v1.virtualizedUpParticles())
			.setParticleTransitionProcedures(v1.buildTransitionProcedures())
			.setParticleStaticCheck(v1.buildParticleStaticCheck())
			.build();

		var actionConstructors = ActionConstructors.newBuilder()
			.put(BurnToken.class, new BurnTokenConstructor())
			.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(DeprecatedUnstakeTokens.class, new DeprecatedUnstakeTokensConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(SystemNextEpoch.class, new NextEpochConstructor())
			.put(SystemNextView.class, new NextViewConstructor())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(SplitToken.class, new SplitTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructor())
			.put(TransferToken.class, new TransferTokensConstructor())
			.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
			.put(UnstakeTokens.class, new UnstakeTokensConstructor())
			.put(UpdateValidator.class, new UpdateValidatorConstructor())
			.build();

		return new ForkConfig(betanet1, actionConstructors, View.of(100000L));
	}

	@ProvidesIntoMap
	@EpochMapKey(epoch = 45L)
	ForkConfig betanetV2() {
		// V2 Betanet ConstraintMachine
		final CMAtomOS v2 = new CMAtomOS(Set.of(TokenDefinitionUtils.getNativeTokenShortCode()));
		v2.load(new ValidatorConstraintScrypt()); // load before TokensConstraintScrypt due to dependency
		v2.load(new TokensConstraintScrypt());
		v2.load(new StakingConstraintScryptV2());
		v2.load(new UniqueParticleConstraintScrypt());
		v2.load(new SystemConstraintScrypt());
		var betanet2 = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(v2.virtualizedUpParticles())
			.setParticleTransitionProcedures(v2.buildTransitionProcedures())
			.setParticleStaticCheck(v2.buildParticleStaticCheck())
			.build();

		var actionConstructors = ActionConstructors.newBuilder()
			.put(BurnToken.class, new BurnTokenConstructor())
			.put(CreateFixedToken.class, new CreateFixedTokenConstructor())
			.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
			.put(DeprecatedUnstakeTokens.class, new DeprecatedUnstakeTokensConstructor())
			.put(MintToken.class, new MintTokenConstructor())
			.put(SystemNextEpoch.class, new NextEpochConstructor())
			.put(SystemNextView.class, new NextViewConstructor())
			.put(RegisterValidator.class, new RegisterValidatorConstructor())
			.put(SplitToken.class, new SplitTokenConstructor())
			.put(StakeTokens.class, new StakeTokensConstructor())
			.put(TransferToken.class, new TransferTokensConstructor())
			.put(UnregisterValidator.class, new UnregisterValidatorConstructor())
			.put(UnstakeTokens.class, new UnstakeTokensConstructor())
			.put(UpdateValidator.class, new UpdateValidatorConstructor())
			.build();

		return new ForkConfig(betanet2, actionConstructors, View.of(10000L));
	}
}
