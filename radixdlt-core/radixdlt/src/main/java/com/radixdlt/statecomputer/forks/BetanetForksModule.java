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
		return new ForkConfig(betanet1, View.of(100000L));
	}

	@ProvidesIntoMap
	@EpochMapKey(epoch = 3L)
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
		return new ForkConfig(betanet2, View.of(10000L));
	}
}
