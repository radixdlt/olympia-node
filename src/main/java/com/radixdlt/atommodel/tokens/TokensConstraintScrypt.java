package com.radixdlt.atommodel.tokens;

import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MAX_DESCRIPTION_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MAX_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MIN_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.VALID_SYMBOL_CHARS;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TokensConstraintScrypt implements ConstraintScrypt {
	private static final ImmutableSet<Integer> VALID_CODEPOINTS = ImmutableSet.copyOf(VALID_SYMBOL_CHARS.chars().iterator());

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	@Override
	public void main(AtomOS os) {
		os.registerParticle(TokenDefinitionParticle.class, TokenDefinitionParticle::getAddress);

		// Symbol constraints
		os.on(TokenDefinitionParticle.class)
			.require(t -> {
				final Result symbolResult;
				final String symbol = t.getSymbol();
				if (symbol == null) {
					symbolResult = Result.error("Symbol: no symbol provided.");
				} else if (symbol.length() < MIN_SYMBOL_LENGTH || symbol.length() > MAX_SYMBOL_LENGTH) {
					symbolResult = Result.error("Symbol: invalid length, must be between " + MIN_SYMBOL_LENGTH + " and "
						+ MAX_SYMBOL_LENGTH + " but is " + symbol.length());
				} else if (!symbol.chars().allMatch(VALID_CODEPOINTS::contains)) {
					symbolResult = Result.error("Symbol: can only use characters '" + VALID_SYMBOL_CHARS + "'.");
				} else {
					symbolResult = Result.success();
				}

				final Result descriptionResult;
				String description = t.getDescription();
				if (description == null || description.isEmpty()) {
					descriptionResult = Result.error("Description: no or empty provided.");
				} else if (description.length() > MAX_DESCRIPTION_LENGTH) {
					descriptionResult = Result.error("Description: invalid length, description must be shorter than or equal to "
						+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
				} else {
					descriptionResult = Result.success();
				}

				final Result permissionsResult;
				if (t.getTokenPermissions() == null || t.getTokenPermissions().keySet().size() != TokenTransition.values().length) {
					permissionsResult = Result.error(
						String.format(
							"Permissions: must be set for all token transitions (%s)",
							Arrays.stream(TokenTransition.values())
								.map(TokenTransition::name)
								.map(String::toLowerCase)
								.collect(Collectors.joining(", "))
						)
					);
				} else {
					permissionsResult = Result.success();
				}

				final Result iconResult;
				String iconUrl = t.getIconUrl();
				if (iconUrl != null && !OWASP_URL_REGEX.matcher(iconUrl).matches()) {
					iconResult = Result.error("Icon: not a valid URL: " + iconUrl);
				} else {
					iconResult = Result.success();
				}

				return Result.combine(symbolResult, descriptionResult, permissionsResult, iconResult);
			});

		os.registerParticle(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAddresses
		);

		// Require Token Definition to be created with unallocated tokens of max supply
		os.newResource(
			TokenDefinitionParticle.class,
			TokenDefinitionParticle::getRRI,
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getTokDefRef,
			(tokDef, unallocated) ->
				Objects.equals(unallocated.getGranularity(), tokDef.getGranularity())
				&& Objects.equals(unallocated.getTokenPermissions(), tokDef.getTokenPermissions())
		);

		os.on(UnallocatedTokensParticle.class)
			.require(u -> Result.of(!u.getAmount().isZero(), "Amount cannot be zero"));

		os.registerParticle(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAddress
		);

		os.on(TransferrableTokensParticle.class)
			.require(u -> Result.of(!u.getAmount().isZero(), "Amount cannot be zero"));

		requireAmountFits(os, TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount, TransferrableTokensParticle::getGranularity);

		os.onFungible(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAmount
		)
			.transitionTo(
				UnallocatedTokensParticle.class,
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
				(from, meta) -> checkSigned(from.getTokDefRef().getAddress(), meta)
			)
			.transitionTo(
				TransferrableTokensParticle.class,
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
				(from, meta) -> from.getTokenPermission(TokenTransition.MINT).check(from.getTokDefRef(), meta)
			);

		os.onFungible(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAmount
		)
			.transitionTo(
				UnallocatedTokensParticle.class,
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
				(from, meta) -> from.getTokenPermission(TokenTransition.BURN).check(from.getTokDefRef(), meta)
			)
			.transitionTo(
				TransferrableTokensParticle.class,
				(from, to) ->
					Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
				(from, meta) -> checkSigned(from.getAddress(), meta)
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
