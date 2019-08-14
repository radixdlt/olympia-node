package com.radixdlt.atommodel.tokens;

import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.procedures.FungibleTransition;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.function.BiFunction;

public class TokensConstraintScrypt implements ConstraintScrypt {


	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			MutableSupplyTokenDefinitionParticle.class,
			MutableSupplyTokenDefinitionParticle::getAddress,
			TokenDefinitionUtils::staticCheck,
			MutableSupplyTokenDefinitionParticle::getRRI
		);

		os.registerParticle(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAddress,
			u -> Result.of(!u.getAmount().isZero(), "Amount cannot be zero"),
			UnallocatedTokensParticle::getTokDefRef
		);

		// Require Token Definition to be created with unallocated tokens of max supply
		os.createTransitionFromRRICombined(
			MutableSupplyTokenDefinitionParticle.class,
			UnallocatedTokensParticle.class,
			(tokDef, unallocated) ->
				Objects.equals(unallocated.getGranularity(), tokDef.getGranularity())
				&& Objects.equals(unallocated.getTokenPermissions(), tokDef.getTokenPermissions())
				&& unallocated.getAmount().equals(UInt256.MAX_VALUE)
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
			}
		);

		// Define mint, transfer, burn transitions
		os.createTransition(
			UnallocatedTokensParticle.class, UnallocatedTokensParticle.class,
			new FungibleTransition<>(
				UnallocatedTokensParticle::getAmount, UnallocatedTokensParticle::getAmount,
				(from, to) ->
					Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			),
			checkInput((in, meta) -> meta.isSignedBy(in.getTokDefRef().getAddress()))
		);
		os.createTransition(
			UnallocatedTokensParticle.class, TransferrableTokensParticle.class,
			new FungibleTransition<>(
				UnallocatedTokensParticle::getAmount, TransferrableTokensParticle::getAmount,
				(from, to) ->
					Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			),
			checkInput((u, meta) -> u.getTokenPermission(TokenTransition.MINT).check(u.getTokDefRef(), meta).isSuccess())
		);
		os.createTransition(
			TransferrableTokensParticle.class, TransferrableTokensParticle.class,
			new FungibleTransition<>(
				TransferrableTokensParticle::getAmount, TransferrableTokensParticle::getAmount,
				(from, to) ->
					Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			),
			checkInput((in, meta) -> meta.isSignedBy(in.getAddress()))
		);
		os.createTransition(
			TransferrableTokensParticle.class, UnallocatedTokensParticle.class,
			new FungibleTransition<>(
				TransferrableTokensParticle::getAmount, UnallocatedTokensParticle::getAmount,
				(from, to) ->
					Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			),
			checkInput((in, meta) -> in.getTokenPermission(TokenTransition.BURN).check(in.getTokDefRef(), meta).isSuccess())
		);
	}

	private static <T extends Particle, U extends Particle> WitnessValidator<T, U> checkInput(BiFunction<T, AtomMetadata, Boolean> check) {
		return (res, in, out, meta) -> {
			switch (res) {
				case POP_OUTPUT:
					return true;
				case POP_INPUT:
				case POP_INPUT_OUTPUT:
					return check.apply(in, meta);
				default:
					throw new IllegalStateException("Unsupported CMAction: " + res);
			}
		};
	}
}
