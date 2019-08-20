package com.radixdlt.atommodel.tokens;

import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.procedures.FungibleTransition;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
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
			p -> p.getRRI().getAddress(),
			TokenDefinitionUtils::staticCheck,
			MutableSupplyTokenDefinitionParticle::getRRI
		);

		os.registerParticle(
			FixedSupplyTokenDefinitionParticle.class,
			p -> p.getRRI().getAddress(),
			TokenDefinitionUtils::staticCheck,
			FixedSupplyTokenDefinitionParticle::getRRI
		);

		os.registerParticle(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAddress,
			u -> {
				if (u.getAmount() == null) {
					return Result.error("amount must not be null");
				}
				if (u.getAmount().isZero()) {
					return Result.error("Amount cannot be zero");
				}
				if (u.getGranularity() == null) {
					return Result.error("granularity must not be null");
				}
				if (u.getGranularity().isZero()) {
					return Result.error("granularity must not be zero");
				}

				return Result.success();
			},
			UnallocatedTokensParticle::getTokDefRef
		);

		os.registerParticle(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAddress,
			tokenParticle -> {
				if (tokenParticle.getAmount() == null) {
					return Result.error("amount must not be null");
				}
				if (tokenParticle.getAmount().isZero()) {
					return Result.error("amount must not be zero");
				}
				if (tokenParticle.getGranularity() == null) {
					return Result.error("granularity must not be null");
				}
				if (tokenParticle.getGranularity().isZero() || !tokenParticle.getAmount().remainder(tokenParticle.getGranularity()).isZero()) {
					return Result.error("amount " + tokenParticle.getAmount() + " does not fit granularity " + tokenParticle.getGranularity());
				}

				return Result.success();
			},
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


		// Define mint, transfer, burn transitions
		os.createTransition(
			UnallocatedTokensParticle.class, UnallocatedTokensParticle.class,
			new FungibleTransition<>(
				UnallocatedTokensParticle::getAmount, UnallocatedTokensParticle::getAmount,
				checkEquals(
					UnallocatedTokensParticle::getGranularity,
					UnallocatedTokensParticle::getGranularity,
					"Granulaties not equal.",
					UnallocatedTokensParticle::getTokenPermissions,
					UnallocatedTokensParticle::getTokenPermissions,
					"Permissions not equal."
				)
			),
			checkInput((in, meta) -> meta.isSignedBy(in.getTokDefRef().getAddress()))
		);
		os.createTransition(
			UnallocatedTokensParticle.class, TransferrableTokensParticle.class,
			new FungibleTransition<>(
				UnallocatedTokensParticle::getAmount, TransferrableTokensParticle::getAmount,
				checkEquals(
					UnallocatedTokensParticle::getGranularity,
					TransferrableTokensParticle::getGranularity,
					"Granulaties not equal.",
					UnallocatedTokensParticle::getTokenPermissions,
					TransferrableTokensParticle::getTokenPermissions,
					"Permissions not equal."
				)
			),
			checkInput((u, meta) -> u.getTokenPermission(TokenTransition.MINT).check(u.getTokDefRef(), meta).isSuccess())
		);
		os.createTransition(
			TransferrableTokensParticle.class, TransferrableTokensParticle.class,
			new FungibleTransition<>(
				TransferrableTokensParticle::getAmount, TransferrableTokensParticle::getAmount,
				checkEquals(
					TransferrableTokensParticle::getGranularity,
					TransferrableTokensParticle::getGranularity,
					"Granulaties not equal.",
					TransferrableTokensParticle::getTokenPermissions,
					TransferrableTokensParticle::getTokenPermissions,
					"Permissions not equal."
				)
			),
			checkInput((in, meta) -> meta.isSignedBy(in.getAddress()))
		);
		os.createTransition(
			TransferrableTokensParticle.class, UnallocatedTokensParticle.class,
			new FungibleTransition<>(
				TransferrableTokensParticle::getAmount, UnallocatedTokensParticle::getAmount,
				checkEquals(
					TransferrableTokensParticle::getGranularity,
					UnallocatedTokensParticle::getGranularity,
					"Granulaties not equal.",
					TransferrableTokensParticle::getTokenPermissions,
					UnallocatedTokensParticle::getTokenPermissions,
					"Permissions not equal."
				)
			),
			checkInput((in, meta) -> in.getTokenPermission(TokenTransition.BURN).check(in.getTokDefRef(), meta).isSuccess())
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

	private static <T extends Particle, U extends Particle> WitnessValidator<T, U> checkInput(BiFunction<T, AtomMetadata, Boolean> check) {
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
