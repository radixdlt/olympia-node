package org.radix.validation;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMResult;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.StateCheckResult;
import com.radixdlt.engine.StateCheckResult.StateCheckResultAcceptor;
import java.util.Collections;
import java.util.Objects;

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

	public CMAtom validate(Atom atom) throws ValidationException {
		Objects.requireNonNull(atom, "atom is required");
		final CMResult result = radixEngine.validate(atom);
		return result.onSuccessElseThrow(e -> {
			CMError cmError = e.iterator().next();
			return new ConstraintMachineValidationException(atom, cmError.getErrorDescription(), cmError.getDataPointer());
		});
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
