package com.radixdlt.middleware2.processing;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.utils.UInt384;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.PreparedAtom;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.atoms.particles.conflict.events.ConflictDetectedEvent;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalProofValidator;
import org.radix.validation.ConstraintMachineValidationException;

import java.util.Collections;

public class EngineAtomEventListener implements AtomEventListener<SimpleRadixEngineAtom> {
	private static final Logger log = Logging.getLogger("EngineAtomEventListener");

	@Override
	public void onCMError(SimpleRadixEngineAtom cmAtom, CMError error) {
		ConstraintMachineValidationException ex = new ConstraintMachineValidationException(cmAtom.getAtom(), error.getErrMsg(), error.getDataPointer());
		ImmutableAtom immutableAtom = cmAtom.getAtom();
		org.radix.atoms.Atom legacyAtom = new org.radix.atoms.Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
		Events.getInstance().broadcast(new AtomExceptionEvent(ex, legacyAtom));
	}

	@Override
	public void onStateStore(SimpleRadixEngineAtom cmAtom) {
		try {
			PreparedAtom preparedAtom = new PreparedAtom(cmAtom, UInt384.ONE);
			Events.getInstance().broadcastWithException(new AtomStoredEvent(preparedAtom));

			ImmutableAtom immutableAtom = cmAtom.getAtom();
			Atom atom = new Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());

			try {
				TemporalProofValidator.validate(atom.getTemporalProof());
			} catch (Exception e) {
				log.error("TemporalProof Validation failed", e);
				Events.getInstance().broadcast(new AtomExceptionEvent(e, atom));
			}
		} catch (Throwable e) {
			log.error("Store of atom failed", e);
		}
	}

	@Override
	public void onVirtualStateConflict(SimpleRadixEngineAtom cmAtom, DataPointer issueParticle) {
		ConstraintMachineValidationException e = new ConstraintMachineValidationException(cmAtom.getAtom(), "Virtual state conflict", issueParticle);
		log.error(e);
		ImmutableAtom immutableAtom = cmAtom.getAtom();
		org.radix.atoms.Atom legacyAtom = new org.radix.atoms.Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
		Events.getInstance().broadcast(new AtomExceptionEvent(e, legacyAtom));
	}

	@Override
	public void onStateConflict(SimpleRadixEngineAtom cmAtom, DataPointer dp, SimpleRadixEngineAtom conflictingAtom) {
		ImmutableAtom cmAtomImmutableAtom = cmAtom.getAtom();
		org.radix.atoms.Atom cmAtomLegacyAtom = new org.radix.atoms.Atom(cmAtomImmutableAtom.getParticleGroups(), cmAtomImmutableAtom.getSignatures(), cmAtomImmutableAtom.getMetaData());
		ImmutableAtom conflictingAtomImmutableAtom = cmAtom.getAtom();
		org.radix.atoms.Atom conflictingAtomLegacyAtom = new org.radix.atoms.Atom(conflictingAtomImmutableAtom.getParticleGroups(), conflictingAtomImmutableAtom.getSignatures(), conflictingAtomImmutableAtom.getMetaData());
		SpunParticle cmAtomIssueParticle = cmAtomImmutableAtom.getSpunParticle(dp);

		final ParticleConflictException conflict = new ParticleConflictException(
				new ParticleConflict(
						cmAtomIssueParticle,
						ImmutableSet.of(cmAtomLegacyAtom, conflictingAtomLegacyAtom)
				));
		AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(conflict, (Atom) cmAtom.getAtom());
		Events.getInstance().broadcast(atomExceptionEvent);
		Events.getInstance().broadcast(new ConflictDetectedEvent(conflict.getConflict()));
		log.error("Conflict: ", conflict);
	}

	@Override
	public void onStateMissingDependency(SimpleRadixEngineAtom cmAtom, DataPointer dp) {
		ImmutableAtom cmAtomImmutableAtom = cmAtom.getAtom();
		org.radix.atoms.Atom cmAtomLegacyAtom = new org.radix.atoms.Atom(cmAtomImmutableAtom.getParticleGroups(), cmAtomImmutableAtom.getSignatures(), cmAtomImmutableAtom.getMetaData());

		SpunParticle issueParticle = cmAtom.getAtom().getSpunParticle(dp);

		final AtomDependencyNotFoundException notFoundException =
				new AtomDependencyNotFoundException(
						String.format("Atom has missing dependencies in transitions: %s", issueParticle.getParticle().getHID()),
						Collections.singleton(issueParticle.getParticle().getHID()),
						cmAtomLegacyAtom
				);

		AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(notFoundException, cmAtomLegacyAtom);
		Events.getInstance().broadcast(atomExceptionEvent);
		log.error(notFoundException);
	}
}
