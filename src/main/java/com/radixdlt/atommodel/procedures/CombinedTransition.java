package com.radixdlt.atommodel.procedures;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import java.util.function.BiFunction;

/**
 * Transition procedure for a transition from one particle type to two particle types.
 */
public final class CombinedTransition<I extends Particle, O extends Particle, V extends Particle> {

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

	private final BiFunction<O, V, Result> combinedCheck;
	private final TypeToken<UsedParticle<O>> typeToken0;
	private final TypeToken<UsedParticle<V>> typeToken1;

	public CombinedTransition(
		Class<O> outputClass0,
		Class<V> outputClass1,
		BiFunction<O, V, Result> combinedCheck
	) {
		this.typeToken0 = new TypeToken<UsedParticle<O>>() { }.where(new TypeParameter<O>() { }, outputClass0);
		this.typeToken1 = new TypeToken<UsedParticle<V>>() { }.where(new TypeParameter<V>() { }, outputClass1);
		this.combinedCheck = combinedCheck;
	}

	public TransitionProcedure<I, VoidUsedData, O, VoidUsedData> getProcedure0() {
		return (in, inUsed, out, outUsed) -> ProcedureResult.popOutput(new UsedParticle<>(typeToken0, out));
	}

	public TransitionProcedure<I, VoidUsedData, V, VoidUsedData> getProcedure1() {
		return (in, inUsed, out, outUsed) -> ProcedureResult.popOutput(new UsedParticle<>(typeToken1, out));
	}

	public TransitionProcedure<I, UsedParticle<V>, O, VoidUsedData> getProcedure2() {
		return (in, inUsed, out, outUsed) -> {
			Result combinedCheckResult = combinedCheck.apply(out, inUsed.usedParticle);
			if (combinedCheckResult.isError()) {
				return ProcedureResult.error(combinedCheckResult.getErrorMessage());
			}

			return ProcedureResult.popInputOutput();
		};
	}

	public TransitionProcedure<I, UsedParticle<O>, V, VoidUsedData> getProcedure3() {
		return (in, inUsed, out, outUsed) -> {
			Result combinedCheckResult = combinedCheck.apply(inUsed.usedParticle, out);
			if (combinedCheckResult.isError()) {
				return ProcedureResult.error(combinedCheckResult.getErrorMessage());
			}

			return ProcedureResult.popInputOutput();
		};
	}
}
