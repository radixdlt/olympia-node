package org.radix.validation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineUtils;
import com.radixdlt.engine.RadixEngineUtils.CMAtomConversionException;
import com.radixdlt.engine.StateCheckResult;
import com.radixdlt.engine.StateCheckResult.StateCheckResultAcceptor;
import com.radixdlt.engine.ValidationResult;
import com.radixdlt.engine.ValidationResult.ValidationResultAcceptor;
import com.radixdlt.utils.UInt384;
import java.util.Collections;
import java.util.Objects;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.radix.atoms.Atom;
import com.radixdlt.constraintmachine.CMAtom;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.exceptions.ValidationException;
import org.radix.modules.Service;

/**
 * Legacy validation handler to remain compatible with old usages in AtomSync and Conflict handlers until Dan's changes are merged
 */
public class ValidationHandler extends Service {
	private final RadixEngine radixEngine;

	public ValidationHandler(RadixEngine radixEngine) {
		this.radixEngine = radixEngine;
	}

	@Override
	public String getName() {
		return "Validation Handler";
	}

	public ValidationResult validate(CMAtom cmAtom) {
		return radixEngine.validate(cmAtom);
	}

	public void stateCheck(CMAtom cmAtom) throws ValidationException {
		StateCheckResult result = radixEngine.stateCheck(cmAtom);
		final AtomicReference<ParticleConflictException> conflictRef = new AtomicReference<>();
		final AtomicReference<AtomDependencyNotFoundException> notFoundRef = new AtomicReference<>();

		result.accept(new StateCheckResultAcceptor() {
			@Override
			public void onConflict(SpunParticle issueParticle, ImmutableAtom conflictAtom) {
				conflictRef.set(
					new ParticleConflictException(
						new ParticleConflict(
							issueParticle,
							ImmutableSet.of((Atom) cmAtom.getAtom(), (Atom) conflictAtom)
						))
				);
			}

			@Override
			public void onMissingDependency(SpunParticle issueParticle) {
				notFoundRef.set(
					new AtomDependencyNotFoundException(
						String.format("Atom has missing dependencies in transitions: %s", issueParticle.getParticle().getHID()),
						Collections.singleton(issueParticle.getParticle().getHID()),
						(Atom) cmAtom.getAtom()
					)
				);
			}
		});

		ParticleConflictException conflict = conflictRef.get();
		if (conflict != null) {
			throw conflict;
		}
		AtomDependencyNotFoundException notFoundException = notFoundRef.get();
		if (notFoundException != null) {
			throw notFoundException;
		}
	}

	@Override
	public void start_impl() {
	}

	@Override
	public void stop_impl() {
	}
}
