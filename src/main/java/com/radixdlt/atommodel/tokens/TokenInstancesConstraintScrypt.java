package com.radixdlt.atommodel.tokens;

import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;
import java.util.Objects;

public class TokenInstancesConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(AtomOS os) {
		os.registerParticle(
			UnallocatedTokensParticle.class,
			"unallocatedtokens",
			UnallocatedTokensParticle::getAddresses
		);

		os.on(UnallocatedTokensParticle.class)
			.require(u -> Result.of(!u.getAmount().isZero(), "Amount cannot be zero"));

		os.registerParticle(
			TransferrableTokensParticle.class,
			"transferredtokens",
			TransferrableTokensParticle::getAddress
		);

		os.on(TransferrableTokensParticle.class)
			.require(u -> Result.of(!u.getAmount().isZero(), "Amount cannot be zero"));

		requireAmountFits(os, TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount, TransferrableTokensParticle::getGranularity);

		os.onFungible(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAmount,
			(u0, u1) -> u0.getTokDefRef().equals(u1.getTokDefRef())
		)
			.requireInitialWith(TokenDefinitionParticle.class, (unallocated, tokDef, meta) -> Result.combine(
				Result.of(unallocated.getTokDefRef().equals(tokDef.getRRI()), "TokenDefRef should be the same"),
				Result.of(unallocated.getGranularity().equals(tokDef.getGranularity()), "Granularity should match"),
				Result.of(unallocated.getTokenPermissions().equals(tokDef.getTokenPermissions()), "Permissions should match")
			))
			.orFrom(
				UnallocatedTokensParticle.class,
				(from, meta) -> checkSigned(from.getTokDefRef().getAddress(), meta),
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			)
			.orFrom(
				TransferrableTokensParticle.class,
				(from, meta) -> from.getTokenPermission(TokenTransition.BURN).check(from.getTokDefRef(), meta),
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			);

		os.onFungible(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			(t0, t1) ->
				Objects.equals(t0.getAddress(), t1.getAddress())
				&& Objects.equals(t0.getTokDefRef(), t1.getTokDefRef())
				&& Objects.equals(t0.getGranularity(), t1.getGranularity())
				&& Objects.equals(t0.getTokenPermissions(), t1.getTokenPermissions())
		)
			.requireFrom(
				UnallocatedTokensParticle.class,
				(from, meta) -> from.getTokenPermission(TokenTransition.MINT).check(from.getTokDefRef(), meta),
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			)
			.orFrom(
				TransferrableTokensParticle.class,
				(from, meta) -> checkSigned(from.getAddress(), meta),
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions())
			);
	}

	private static <T extends Particle> void requireAmountFits(
		AtomOS os,
		Class<T> cls,
		ParticleToAmountMapper<T> particleToAmountMapper,
		ParticleToAmountMapper<T> particleToGranularityMapper
	) {
		os.on(cls)
			.require(particle -> {
				UInt256 amount = particleToAmountMapper.amount(particle);
				if (amount == null) {
					return Result.error("amount must not be null");
				}
				if (amount.isZero()) {
					return Result.error("amount must not be zero");
				}
				UInt256 granularity = particleToGranularityMapper.amount(particle);
				if (granularity == null) {
					return Result.error("granularity must not be null");
				}
				if (granularity.isZero() || !amount.remainder(granularity).isZero()) {
					return Result.error("amount " + amount + " does not fit granularity " + granularity);
				}

				return Result.success();
			});
	}

	private static Result checkSigned(RadixAddress fromAddress, AtomMetadata metadata) {
		if (!metadata.isSignedBy(fromAddress)) {
			return Result.error("must be signed by source address: " + fromAddress);
		}

		return Result.success();
	}
}
