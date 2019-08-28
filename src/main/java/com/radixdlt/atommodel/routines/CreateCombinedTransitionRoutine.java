package com.radixdlt.atommodel.routines;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.function.BiFunction;

/**
 * Transition procedure for a transition from one particle type to two particle types.
 */
public final class CreateCombinedTransitionRoutine<I extends Particle, O extends Particle, V extends Particle> implements ConstraintRoutine {

	public static class UsedParticle<P extends Particle> implements UsedData {
		private final P usedParticle;
		private final TypeToken<UsedParticle<P>> typeToken;
		UsedParticle(TypeToken<UsedParticle<P>> typeToken, P usedParticle) {
			this.typeToken = typeToken;
			this.usedParticle = usedParticle;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return typeToken;
		}
	}
	private final Class<I> inputClass;
	private final Class<O> outputClass0;
	private final Class<V> outputClass1;
	private final BiFunction<O, V, Result> combinedCheck;
	private final TypeToken<UsedParticle<O>> typeToken0;
	private final TypeToken<UsedParticle<V>> typeToken1;
	private final WitnessValidator<I> inputWitnessValidator;

	public CreateCombinedTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass0,
		Class<V> outputClass1,
		BiFunction<O, V, Result> combinedCheck,
		WitnessValidator<I> inputWitnessValidator
	) {
		this.inputClass = inputClass;
		this.outputClass0 = outputClass0;
		this.outputClass1 = outputClass1;

		this.typeToken0 = new TypeToken<UsedParticle<O>>() { }.where(new TypeParameter<O>() { }, outputClass0);
		this.typeToken1 = new TypeToken<UsedParticle<V>>() { }.where(new TypeParameter<V>() { }, outputClass1);
		this.combinedCheck = combinedCheck;
		this.inputWitnessValidator = inputWitnessValidator;
	}

	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(VoidUsedData.class), outputClass0, TypeToken.of(VoidUsedData.class)),
			getProcedure0()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(VoidUsedData.class), outputClass1, TypeToken.of(VoidUsedData.class)),
			getProcedure1()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, typeToken1, outputClass0, TypeToken.of(VoidUsedData.class)),
			getProcedure2()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, typeToken0, outputClass1, TypeToken.of(VoidUsedData.class)),
			getProcedure3()
		);
	}

	public TransitionProcedure<I, VoidUsedData, O, VoidUsedData> getProcedure0() {
		return (in, inUsed, out, outUsed) -> ProcedureResult.popOutput(
			new UsedParticle<>(typeToken0, out),
			(o, w) -> WitnessValidatorResult.success()
		);
	}

	public TransitionProcedure<I, VoidUsedData, V, VoidUsedData> getProcedure1() {
		return (in, inUsed, out, outUsed) -> ProcedureResult.popOutput(
			new UsedParticle<>(typeToken1, out),
			(o, w) -> WitnessValidatorResult.success()
		);
	}

	public TransitionProcedure<I, UsedParticle<V>, O, VoidUsedData> getProcedure2() {
		return (in, inUsed, out, outUsed) -> {
			Result combinedCheckResult = combinedCheck.apply(out, inUsed.usedParticle);
			if (combinedCheckResult.isError()) {
				return ProcedureResult.error(combinedCheckResult.getErrorMessage());
			}

			return ProcedureResult.popInputOutput(
				inputWitnessValidator,
				(o, w) -> WitnessValidatorResult.success()
			);
		};
	}

	public TransitionProcedure<I, UsedParticle<O>, V, VoidUsedData> getProcedure3() {
		return (in, inUsed, out, outUsed) -> {
			Result combinedCheckResult = combinedCheck.apply(inUsed.usedParticle, out);
			if (combinedCheckResult.isError()) {
				return ProcedureResult.error(combinedCheckResult.getErrorMessage());
			}

			return ProcedureResult.popInputOutput(
				inputWitnessValidator,
				(o, w) -> WitnessValidatorResult.success()
			);
		};
	}
}
