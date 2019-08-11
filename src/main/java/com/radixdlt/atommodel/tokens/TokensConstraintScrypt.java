package com.radixdlt.atommodel.tokens;

import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MAX_DESCRIPTION_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MAX_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MIN_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.VALID_SYMBOL_CHARS;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.procedures.FungibleTransition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TokensConstraintScrypt implements ConstraintScrypt {
	private static final ImmutableSet<Integer> VALID_CODEPOINTS = ImmutableSet.copyOf(VALID_SYMBOL_CHARS.chars().iterator());

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			TokenDefinitionParticle.class,
			TokenDefinitionParticle::getAddress,
			t -> {
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
			}
		);

		os.registerParticle(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAddress,
			u -> Result.of(!u.getAmount().isZero(), "Amount cannot be zero")
		);

		// Require Token Definition to be created with unallocated tokens of max supply
		os.newRRIResourceCombined(
			TokenDefinitionParticle.class,
			TokenDefinitionParticle::getRRI,
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getTokDefRef,
			(tokDef, unallocated) ->
				Objects.equals(unallocated.getGranularity(), tokDef.getGranularity())
				&& Objects.equals(unallocated.getTokenPermissions(), tokDef.getTokenPermissions())
		);

		os.registerParticle(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAddress,
			t -> {
				if (t.getAmount() == null) {
					return Result.error("amount must not be null");
				}
				if (t.getAmount().isZero()) {
					return Result.error("amount must not be zero");
				}
				if (t.getGranularity() == null) {
					return Result.error("granularity must not be null");
				}
				if (t.getGranularity().isZero() || !t.getAmount().remainder(t.getGranularity()).isZero()) {
					return Result.error("amount " + t.getAmount() + " does not fit granularity " + t.getGranularity());
				}

				return Result.success();
			}
		);


		os.newTransition(new FungibleTransition<>(
			UnallocatedTokensParticle.class, UnallocatedTokensParticle::getAmount,
			UnallocatedTokensParticle.class, UnallocatedTokensParticle::getAmount,
			(from, to) ->
				Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
			(from, meta) -> checkSigned(from.getTokDefRef().getAddress(), meta)
		));
		os.newTransition(new FungibleTransition<>(
			UnallocatedTokensParticle.class, UnallocatedTokensParticle::getAmount,
			TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount,
			(from, to) ->
				Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
			(from, meta) -> from.getTokenPermission(TokenTransition.MINT).check(from.getTokDefRef(), meta)
		));
		os.newTransition(new FungibleTransition<>(
			TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount,
			TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount,
			(from, to) ->
				Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
			(from, meta) -> checkSigned(from.getAddress(), meta)
		));
		os.newTransition(new FungibleTransition<>(
			TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount,
			UnallocatedTokensParticle.class, UnallocatedTokensParticle::getAmount,
			(from, to) ->
				Objects.equals(from.getTokDefRef(), to.getTokDefRef())
					&& Objects.equals(from.getGranularity(), to.getGranularity())
					&& Objects.equals(from.getTokenPermissions(), to.getTokenPermissions()),
			(from, meta) -> from.getTokenPermission(TokenTransition.BURN).check(from.getTokDefRef(), meta)
		));
	}

	private static Result checkSigned(RadixAddress fromAddress, AtomMetadata metadata) {
		if (!metadata.isSignedBy(fromAddress)) {
			return Result.error("must be signed by source address: " + fromAddress);
		}

		return Result.success();
	}
}
