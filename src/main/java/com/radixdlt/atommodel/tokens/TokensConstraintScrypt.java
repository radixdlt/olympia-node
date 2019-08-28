package com.radixdlt.atommodel.tokens;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atommodel.procedures.FungibleTransition.UsedAmount;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.procedures.FungibleTransition;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.store.SpinStateMachine.Transition;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Scrypt which defines how tokens are managed.
 */
public class TokensConstraintScrypt implements ConstraintScrypt {

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			MutableSupplyTokenDefinitionParticle.class,
			particle -> particle.getRRI().getAddress(),
			TokenDefinitionUtils::staticCheck,
			MutableSupplyTokenDefinitionParticle::getRRI
		);

		os.registerParticle(
			FixedSupplyTokenDefinitionParticle.class,
			particle -> particle.getRRI().getAddress(),
			TokenDefinitionUtils::staticCheck,
			FixedSupplyTokenDefinitionParticle::getRRI
		);

		os.registerParticle(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAddress,
			TokenDefinitionUtils::staticCheck,
			UnallocatedTokensParticle::getTokDefRef
		);

		os.registerParticle(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAddress,
			TokenDefinitionUtils::staticCheck,
			TransferrableTokensParticle::getTokDefRef
		);

		// Require Token Definition to be created with unallocated tokens of max supply
		os.createTransitionFromRRICombined(
			MutableSupplyTokenDefinitionParticle.class,
			UnallocatedTokensParticle.class,
			(tokDef, unallocated) -> {
				if (!Objects.equals(unallocated.getGranularity(), tokDef.getGranularity())) {
					return Result.error("Granularities not equal.");
				}

				if (!Objects.equals(unallocated.getTokenPermissions(), tokDef.getTokenPermissions())) {
					return Result.error("Permissions not equal.");
				}

				if (!unallocated.getAmount().equals(UInt256.MAX_VALUE)) {
					return Result.error("Unallocated amount must be UInt256.MAX_VALUE but was " + unallocated.getAmount());
				}

				return Result.success();
			}
		);

		os.createTransitionFromRRICombined(
			FixedSupplyTokenDefinitionParticle.class,
			TransferrableTokensParticle.class,
			(tokDef, transferrable) -> {
				if (!Objects.equals(tokDef.getGranularity(), transferrable.getGranularity())) {
					return Result.error("Granularities not equal.");
				}

				if (!Objects.equals(tokDef.getSupply(), transferrable.getAmount())) {
					return Result.error("Supply and amount are not equal.");
				}

				if (!transferrable.getTokenPermissions().isEmpty()) {
					return Result.error("Transferrable tokens of a fixed supply token must be empty.");
				}

				return Result.success();
			}
		);

		// Unallocated movement
		FungibleTransition<UnallocatedTokensParticle, UnallocatedTokensParticle> unallocatedTransitions = new FungibleTransition<>(
			UnallocatedTokensParticle::getAmount, UnallocatedTokensParticle::getAmount,
			checkEquals(
				UnallocatedTokensParticle::getGranularity,
				UnallocatedTokensParticle::getGranularity,
				"Granulaties not equal.",
				UnallocatedTokensParticle::getTokenPermissions,
				UnallocatedTokensParticle::getTokenPermissions,
				"Permissions not equal."
			)
		);
		createFungibleTransitions(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle.class,
			unallocatedTransitions,
			checkInput((in, meta) -> meta.isSignedBy(in.getTokDefRef().getAddress().getKey())),
			os
		);

		// Mint
		FungibleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> mintTransitions = new FungibleTransition<>(
			UnallocatedTokensParticle::getAmount, TransferrableTokensParticle::getAmount,
			checkEquals(
				UnallocatedTokensParticle::getGranularity,
				TransferrableTokensParticle::getGranularity,
				"Granulaties not equal.",
				UnallocatedTokensParticle::getTokenPermissions,
				TransferrableTokensParticle::getTokenPermissions,
				"Permissions not equal."
			)
		);
		createFungibleTransitions(
			UnallocatedTokensParticle.class,
			TransferrableTokensParticle.class,
			mintTransitions,
			checkInput((in, meta) -> in.getTokenPermission(TokenTransition.MINT).check(in.getTokDefRef(), meta).isSuccess()),
			os
		);

		// Transfers
		FungibleTransition<TransferrableTokensParticle, TransferrableTokensParticle> transferTransitions = new FungibleTransition<>(
			TransferrableTokensParticle::getAmount, TransferrableTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::getGranularity,
				TransferrableTokensParticle::getGranularity,
				"Granulaties not equal.",
				TransferrableTokensParticle::getTokenPermissions,
				TransferrableTokensParticle::getTokenPermissions,
				"Permissions not equal."
			)
		);
		createFungibleTransitions(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle.class,
			transferTransitions,
			checkInput((in, meta) -> meta.isSignedBy(in.getTokDefRef().getAddress().getKey())),
			os
		);

		// Burns
		FungibleTransition<TransferrableTokensParticle, UnallocatedTokensParticle> burnTransitions = new FungibleTransition<>(
			TransferrableTokensParticle::getAmount, UnallocatedTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::getGranularity,
				UnallocatedTokensParticle::getGranularity,
				"Granulaties not equal.",
				TransferrableTokensParticle::getTokenPermissions,
				UnallocatedTokensParticle::getTokenPermissions,
				"Permissions not equal."
			)
		);
		createFungibleTransitions(
			TransferrableTokensParticle.class,
			UnallocatedTokensParticle.class,
			burnTransitions,
			checkInput((in, meta) -> in.getTokenPermission(TokenTransition.BURN).check(in.getTokDefRef(), meta).isSuccess()),
			os
		);
	}

	private static <I extends Particle, O extends Particle> void createFungibleTransitions(
		Class<I> inputClass,
		Class<O> outputClass,
		FungibleTransition<I, O> transition,
		WitnessValidator<I, O> witnessValidator,
		SysCalls os
	) {
		os.createTransition(
			inputClass,
			TypeToken.of(VoidUsedData.class),
			outputClass,
			TypeToken.of(VoidUsedData.class),
			transition.getProcedure0(),
			witnessValidator
		);
		os.createTransition(
			inputClass,
			TypeToken.of(UsedAmount.class),
			outputClass,
			TypeToken.of(VoidUsedData.class),
			transition.getProcedure1(),
			witnessValidator
		);
		os.createTransition(
			inputClass,
			TypeToken.of(VoidUsedData.class),
			outputClass,
			TypeToken.of(UsedAmount.class),
			transition.getProcedure2(),
			witnessValidator
		);
	}

	private static <T, U, V> BiFunction<T, U, Result> checkEquals(
		Function<T, V> firstMapper0, Function<U, V> firstMapper1, String firstErrorMessage,
		Function<T, V> secondMapper0, Function<U, V> secondMapper1, String secondErrorMessage
	) {
		return (t, u) -> {
			if (!Objects.equals(firstMapper0.apply(t), firstMapper1.apply(u))) {
				return Result.error(firstErrorMessage);
			}

			if (!Objects.equals(secondMapper0.apply(t), secondMapper1.apply(u))) {
				return Result.error(secondErrorMessage);
			}

			return Result.success();
		};
	}

	private static <T extends Particle, U extends Particle> WitnessValidator<T, U> checkInput(BiFunction<T, WitnessData, Boolean> check) {
		return (res, in, out, meta) -> {
			switch (res) {
				case POP_OUTPUT:
					return WitnessValidatorResult.success();
				case POP_INPUT:
				case POP_INPUT_OUTPUT:
					return check.apply(in, meta) ? WitnessValidatorResult.success() : WitnessValidatorResult.error("Permission not allowed.");
				default:
					throw new IllegalStateException("Unsupported CMAction: " + res);
			}
		};
	}
}
