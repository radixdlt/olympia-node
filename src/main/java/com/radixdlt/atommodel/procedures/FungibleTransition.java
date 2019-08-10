package com.radixdlt.atommodel.procedures;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.utils.UInt256;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleTransition<T extends Particle, U extends Particle> implements ConstraintProcedure {
	private final Class<T> inputParticleClass;
	private final Function<T, UInt256> inputAmountMapper;
	private final Class<U> outputParticleClass;
	private final Function<U, UInt256> outputAmountMapper;
	private final BiPredicate<T, U> transition;
	private final WitnessValidator<T> witnessValidator;


	public FungibleTransition(
		Class<T> inputParticleClass,
		Function<T, UInt256> inputAmountMapper,
		Class<U> outputParticleClass,
		Function<U, UInt256> outputAmountMapper,
		BiPredicate<T, U> transition,
		WitnessValidator<T> witnessValidator
	) {
		this.inputParticleClass = inputParticleClass;
		this.inputAmountMapper = inputAmountMapper;
		this.outputParticleClass = outputParticleClass;
		this.outputAmountMapper = outputAmountMapper;
		this.transition = transition;
		this.witnessValidator = witnessValidator;
	}

	@Override
	public ImmutableSet<Pair<Class<? extends Particle>, Class<? extends Particle>>> supports() {
		return ImmutableSet.of(Pair.of(inputParticleClass, outputParticleClass));
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		Particle outputParticle,
		AtomMetadata metadata
	) {
		switch (result) {
			case POP_OUTPUT:
				return true;
			case POP_INPUT:
			case POP_INPUT_OUTPUT:
				return witnessValidator.validate((T) inputParticle, metadata).isSuccess();
			default:
				throw new IllegalStateException();
		}
	}

	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		Particle outputParticle,
		AtomicReference<Object> outputData
	) {
		if (!transition.test((T) inputParticle, (U) outputParticle)) {
			return ProcedureResult.ERROR;
		}

		UInt256 inputAmount = inputData.get() == null
			? inputAmountMapper.apply((T) inputParticle)
			: (UInt256) inputData.get();
		UInt256 outputAmount = outputData.get() == null
			? outputAmountMapper.apply((U) outputParticle)
			: (UInt256) outputData.get();

		int compare = inputAmount.compareTo(outputAmount);
		if (compare == 0) {
			return ProcedureResult.POP_INPUT_OUTPUT;
		} else if (compare > 0) {
			inputData.set(inputAmount.subtract(outputAmount));
			return ProcedureResult.POP_OUTPUT;
		} else {
			outputData.set(outputAmount.subtract(inputAmount));
			return ProcedureResult.POP_INPUT;
		}
	}
}