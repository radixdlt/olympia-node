package com.radixdlt.atommodel.tokens;

import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.RRI;
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

		os.registerParticle(TransferrableTokensParticle.class, "transferredtokens", TransferrableTokensParticle::getAddress);

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
			.orFrom(1, UnallocatedTokensParticle.class, (from, to, meta) -> Result.combine(
				checkSigned(from.getTokDefRef().getAddress(), meta),
				checkType(from.getTokDefRef(), to.getTokDefRef()),
				checkGranularity(from.getGranularity(), to.getGranularity()),
				Result.of(from.getTokenPermissions().equals(to.getTokenPermissions()), "Permissions should match")
			))
			.orFrom(1, TransferrableTokensParticle.class, (from, to, meta) -> Result.combine(
				Result.of(from.getAddress().equals(from.getTokDefRef().getAddress()), "Must burn to same account"),
				to.getTokenPermission(TokenTransition.BURN).check(from.getTokDefRef(), meta),
				checkType(from.getTokDefRef(), to.getTokDefRef()),
				checkSigned(from.getTokDefRef().getAddress(), meta),
				checkGranularity(from.getGranularity(), to.getGranularity()),
				Result.of(from.getTokenPermissions().equals(to.getTokenPermissions()), "Permissions should match")
			));

		os.onFungible(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			(t0, t1) -> t0.getAddress().equals(t1.getAddress()) && t0.getTokDefRef().equals(t1.getTokDefRef())
		)
			.requireFrom(1, UnallocatedTokensParticle.class, (from, to, meta) -> Result.combine(
				Result.of(to.getAddress().equals(to.getTokDefRef().getAddress()), "Must mint to same account"),
				from.getTokenPermission(TokenTransition.MINT).check(from.getTokDefRef(), meta),
				checkType(from.getTokDefRef(), to.getTokDefRef()),
				checkGranularity(from.getGranularity(), to.getGranularity()),
				Result.of(to.getTokenPermissions().equals(from.getTokenPermissions()), "Permissions must be the same")
			))
			.orFrom(1, TransferrableTokensParticle.class, (from, to, meta) -> Result.combine(
				checkSigned(from.getAddress(), meta),
				checkType(from.getTokDefRef(), to.getTokDefRef()),
				checkGranularity(from.getGranularity(), to.getGranularity()),
				Result.of(to.getTokenPermissions().equals(from.getTokenPermissions()), "Permissions must be the same"),
				checkPlanck(from.getPlanck(), to.getPlanck())));
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

	// TODO Eventually, plancks should be checked by a lower level construct (e.g. kernel scrypts).
	// TODO However, currently there is no primitive transition type that the kernel could program
	// TODO additional constraints against, and it cannot restrict transitions it doesn't know about.
	private static Result checkPlanck(long fromPlanckTime, long toPlanckTime) {
		if (toPlanckTime < fromPlanckTime) {
			return Result.error("output planck time must be >= input planck time: " + toPlanckTime + " < " + fromPlanckTime);
		}

		return Result.success();
	}

	private static Result checkSigned(RadixAddress fromAddress, AtomMetadata metadata) {
		if (!metadata.isSignedBy(fromAddress)) {
			return Result.error("must be signed by source address: " + fromAddress);
		}

		return Result.success();
	}

	private static Result checkType(RRI fromType, RRI toType) {
		if (!Objects.equals(fromType, toType)) {
			return Result.error("token types must be equal: " + fromType + " != " + toType);
		}

		return Result.success();
	}

	private static Result checkGranularity(UInt256 fromGranularity, UInt256 toGranularity) {
		if (!Objects.equals(fromGranularity, toGranularity)) {
			return Result.error("granularities must be equal: " + fromGranularity + " != " + toGranularity);
		}

		return Result.success();
	}
}
