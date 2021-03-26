/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.validators;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.constraintmachine.Particle;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Action mapper for {@link RegisterValidatorAction}s, implemented using {@link ValidatorRegistrationState}.
 */
public class RegisterValidatorActionMapper implements StatefulActionToParticleGroupsMapper<RegisterValidatorAction> {
	@Override
	public Set<ShardedParticleStateId> requiredState(RegisterValidatorAction action) {
		return ImmutableSet.of(
			ShardedParticleStateId.of(RegisteredValidatorParticle.class, action.getValidator()),
			ShardedParticleStateId.of(UnregisteredValidatorParticle.class, action.getValidator())
		);
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(RegisterValidatorAction action, Stream<Particle> store) throws StageActionException {
		ValidatorRegistrationState currentState = ValidatorRegistrationState.from(store, action.getValidator());
		var particle = currentState.asParticle();
		var isVirtual = particle instanceof UnregisteredValidatorParticle && ((UnregisteredValidatorParticle) particle).getNonce() == 0;
		var builder = ParticleGroup.builder();
		if (isVirtual) {
			builder.virtualSpinDown(particle);
		} else {
			builder.spinDown(SubstateId.of(particle));
		}
		builder.spinUp(currentState.register(action.getUrl(), action.getAllowedDelegators()));

		return ImmutableList.of(builder.build());
	}

}
