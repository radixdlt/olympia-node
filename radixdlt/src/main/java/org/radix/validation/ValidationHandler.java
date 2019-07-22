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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.radix.atoms.Atom;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.exceptions.ValidationException;
import org.radix.modules.Service;

/**
 * Legacy validation handler to remain compatible with old usages in AtomSync and Conflict handlers until Dan's changes are merged
 */
public class ValidationHandler extends Service {
	private final ConstraintMachine constraintMachine;
	private final RadixEngine radixEngine;

	public ValidationHandler(ConstraintMachine constraintMachine, CMStore cmStore) {
		this.radixEngine = new RadixEngine(constraintMachine, cmStore);
		this.constraintMachine = constraintMachine;
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
		final CompletableFuture<Pair<CMAtom, UInt384>> cmAtomCompletableFuture = new CompletableFuture<>();

		result.accept(new ValidationResultAcceptor() {
			@Override
			public void onSuccess(ImmutableMap<String, Object> computed) {
				Object result = computed.get("mass");
				if (result == null) {
					throw new NullPointerException("mass does not exist");
				}
				cmAtomCompletableFuture.complete(Pair.of(cmAtom, UInt384.class.cast(result)));
			}

			@Override
			public void onError(Set<CMError> errors) {
				CMError cmError = errors.iterator().next();
				cmAtomCompletableFuture.completeExceptionally(new ConstraintMachineValidationException(atom, cmError.getErrorDescription(), cmError.getDataPointer()));
			}
		});

		try {
			return cmAtomCompletableFuture.get();
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ValidationException) {
				throw (ValidationException) e.getCause();
			}
			throw new IllegalStateException();
		} catch (InterruptedException e) {
			throw new IllegalStateException();
		}
	}

	public void stateCheck(CMAtom cmAtom) throws Exception {
		StateCheckResult result = radixEngine.stateCheck(cmAtom);
		result.accept(new StateCheckResultAcceptor() {
			@Override
			public void onConflict(SpunParticle issueParticle, ImmutableAtom conflictAtom) throws Exception {
				throw new ParticleConflictException(
					new ParticleConflict(
						issueParticle,
						ImmutableSet.of((Atom) cmAtom.getAtom(), (Atom) conflictAtom)
					));
			}

			@Override
			public void onMissingDependency(SpunParticle issueParticle) throws Exception {
				throw new AtomDependencyNotFoundException(
					String.format("Atom has missing dependencies in transitions: %s", issueParticle.getParticle().getHID()),
					Collections.singleton(issueParticle.getParticle().getHID()),
					(Atom) cmAtom.getAtom()
				);
			}
		});
	}

	/**
	 * Accessor to the constraint machine.
	 * TODO: remove this in future after refactor
	 *
	 * @return constraint machine being used
	 */
	public ConstraintMachine getConstraintMachine() {
		return constraintMachine;
	}

	@Override
	public void start_impl() {
	}

	@Override
	public void stop_impl() {
	}
}
