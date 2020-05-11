/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.processing;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.AID;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.serialization.Serialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.events.Events;
import org.radix.validation.ConstraintMachineValidationException;

import java.util.Collections;
import java.util.stream.Collectors;

public class EngineAtomEventListener implements AtomEventListener<SimpleRadixEngineAtom> {
	private static final Logger log = LogManager.getLogger("middleware2.eventListener");
	private final Serialization serialization;

	public EngineAtomEventListener(Serialization serialization) {
		this.serialization = serialization;
	}

	@Override
	public void onCMError(SimpleRadixEngineAtom atom, CMError error) {
		ConstraintMachineValidationException ex = new ConstraintMachineValidationException(atom.getAtom(), error.getErrMsg(), error.getDataPointer());
		Events.getInstance().broadcast(new AtomExceptionEvent(ex, atom.getAID()));
	}

	@Override
	public void onStateStore(SimpleRadixEngineAtom atom) {
		try {
			EngineAtomIndices engineAtomIndices = EngineAtomIndices.from(atom, serialization);
			Events.getInstance().broadcastWithException(new AtomStoredEvent(atom, () ->
				engineAtomIndices.getDuplicateIndices().stream()
					.filter(e -> e.getPrefix() == EngineAtomIndices.IndexType.DESTINATION.getValue())
					.map(e -> EngineAtomIndices.toEUID(e.asKey()))
					.collect(Collectors.toSet()))
			);
		} catch (Throwable e) {
			log.error("Store of atom failed", e);
		}
	}

	@Override
	public void onVirtualStateConflict(SimpleRadixEngineAtom atom, DataPointer issueParticle) {
		ConstraintMachineValidationException e = new ConstraintMachineValidationException(atom.getAtom(), "Virtual state conflict", issueParticle);
		log.error("Virtual state conflict", e);
		Events.getInstance().broadcast(new AtomExceptionEvent(e, atom.getAID()));
	}

	@Override
	public void onStateConflict(SimpleRadixEngineAtom atom, DataPointer dp, SimpleRadixEngineAtom conflictingAtom) {
		final ParticleConflictException conflict = new ParticleConflictException(
				new ParticleConflict(dp, ImmutableSet.of(atom.getAID(), conflictingAtom.getAID())
				));
		AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(conflict, atom.getAID());
		Events.getInstance().broadcast(atomExceptionEvent);
	}

	@Override
	public void onStateMissingDependency(AID atomId, Particle particle) {
		final AtomDependencyNotFoundException notFoundException =
			new AtomDependencyNotFoundException(
				String.format("Atom has missing dependencies in transitions: %s", particle.euid()),
				Collections.singleton(particle.euid())
			);

		AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(notFoundException, atomId);
		Events.getInstance().broadcast(atomExceptionEvent);
	}
}
