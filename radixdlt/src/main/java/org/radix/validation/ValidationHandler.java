package org.radix.validation;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.stream.Collectors;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomDependencyNotFoundException;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.CMResult;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import com.radixdlt.atoms.SpunParticle;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import com.radixdlt.store.CMStore;
import com.radixdlt.common.Pair;
import org.radix.exceptions.ValidationException;
import org.radix.modules.Service;

/**
 * Legacy validation handler to remain compatible with old usages in AtomSync and Conflict handlers until Dan's changes are merged
 */
public class ValidationHandler extends Service {
	private final ConstraintMachine constraintMachine;
	private final CMStore cmStore;

	public ValidationHandler(ConstraintMachine constraintMachine, CMStore cmStore) {
		this.constraintMachine = constraintMachine;
		this.cmStore = constraintMachine.virtualize(cmStore);
	}

	@Override
	public String getName() {
		return "Validation Handler";
	}

	/**
	 * Legacy validate for Atoms/AtomSync to remain compatible until Dan's changes are merged
	 * TODO: Figure out what Mode is used for or get rid of it if unneeded
	 */
	public CMAtom validate(Atom atom) throws ValidationException {
		Objects.requireNonNull(atom, "atom is required");

		final CMResult result = constraintMachine.validate(atom, false);
		return result.onSuccessElseThrow(e -> {
			CMError cmError = e.iterator().next();
			return new ConstraintMachineValidationException(atom, cmError.getErrorDescription(), cmError.getDataPointer());
		});
	}

	public void stateCheck(CMAtom cmAtom) throws ValidationException {
		final Atom atom = (Atom) cmAtom.getAtom();
		// TODO: Optimize these collectors out
		Map<TransitionCheckResult, List<Pair<DataPointer, TransitionCheckResult>>> spinCheckResults = cmAtom.getParticles()
			.stream()
			.map(cmParticle -> {
				// First spun is the only one we need to check
				final Spin nextSpin = cmParticle.getNextSpin();
				final Particle particle = cmParticle.getParticle();
				final DataPointer dataPointer = cmParticle.getDataPointer();
				final TransitionCheckResult spinCheck = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin, cmStore
				);

				return Pair.of(dataPointer, spinCheck);
			})
			.collect(Collectors.groupingBy(Pair::getSecond));

		if (spinCheckResults.get(TransitionCheckResult.MISSING_STATE_FROM_UNSUPPORTED_SHARD) != null) {
			// Could be missing state needed from other shards. This is okay.
			// TODO: Log
		}

		if (spinCheckResults.get(TransitionCheckResult.ILLEGAL_TRANSITION_TO) != null ||
			spinCheckResults.get(TransitionCheckResult.MISSING_STATE) != null) {
			throw new IllegalStateException("Should not be here. This should be caught by Constraint Machine Stateless validation.");
		}

		if (spinCheckResults.get(TransitionCheckResult.CONFLICT) != null) {
			// TODO !!! This is a hack! What if there are multiple conflicts in an atom?
			// TODO !!! Current conflict handling only supports one conflicting particle.
			// TODO !!! This should be investigated and fixed asap. See RLAU-1076.
			// TODO !!! This also rejects multiple internal conflicts, which may not be ideal.
			final Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.CONFLICT).get(0);
			final SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);

			// TODO: Refactor so that two DB fetches aren't required to get conflicting atoms
			// TODO Because we're checking SpunParticles I understand there can only be one of
			// them in store as they are unique.
			//
			// Modified StateProviderFromStore.getAtomsContaining to be singular based on the
			// above assumption.
			final Atom conflictAtom = (Atom) cmStore.getAtomContaining(issueParticle);

			throw new ParticleConflictException(new ParticleConflict(issueParticle, ImmutableSet.of(atom, conflictAtom)));
		}

		// TODO: Add ALL missing dependencies for optimization
		if (spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY) != null)  {
			Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY).get(0);
			SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);

			throw new AtomDependencyNotFoundException(
				String.format("Atom has missing dependencies in transitions: %s", issueParticle.getParticle().getHID()),
				Collections.singleton(issueParticle.getParticle().getHID()),
				atom
			);
		}
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
