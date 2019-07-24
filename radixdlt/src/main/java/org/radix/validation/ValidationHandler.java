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

	public Pair<CMAtom, UInt384> validate(Atom atom) throws ValidationException {
		Objects.requireNonNull(atom, "atom is required");
		final CMAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (CMAtomConversionException e) {
			CMError cmError = e.getErrors().iterator().next();
			throw new ConstraintMachineValidationException(atom, cmError.getErrorDescription(), cmError.getDataPointer());
		}

		final ValidationResult result = radixEngine.validate(cmAtom);
		final AtomicReference<Pair<CMAtom, UInt384>> resultRef = new AtomicReference<>();
		final AtomicReference<Set<CMError>> errorsRef = new AtomicReference<>();

		result.accept(new ValidationResultAcceptor() {
			@Override
			public void onSuccess(CMAtom cmAtom, ImmutableMap<String, Object> computed) {
				Object result = computed.get("mass");
				if (result == null) {
					throw new NullPointerException("mass does not exist");
				}
				resultRef.set(Pair.of(cmAtom, (UInt384) result));
			}

			@Override
			public void onError(CMAtom cmAtom, Set<CMError> errors) {
				errorsRef.set(errors);
			}
		});

		final Pair<CMAtom, UInt384> r = resultRef.get();
		if (r != null) {
			return r;
		} else {
			CMError cmError = errorsRef.get().iterator().next();
			throw new ConstraintMachineValidationException(atom, cmError.getErrorDescription(), cmError.getDataPointer());
		}
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
