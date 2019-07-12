package com.radixdlt.atommodel.tokens;

import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MAX_DESCRIPTION_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MAX_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.MIN_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.VALID_SYMBOL_CHARS;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TokenDefinitionConstraintScrypt implements ConstraintScrypt {

	private static final ImmutableSet<Integer> VALID_CODEPOINTS = ImmutableSet.copyOf(VALID_SYMBOL_CHARS.chars().iterator());

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	@Override
	public void main(AtomOS os) {
		os.registerParticle(TokenDefinitionParticle.class, "tokens", TokenDefinitionParticle::getOwner);

		// Require Token Definition to be created with unallocated tokens of max supply
		os.onIndexed(TokenDefinitionParticle.class, TokenDefinitionParticle::getRRI)
			.requireInitial((tok, meta) -> Result.of(meta.isSignedBy(tok.getOwner()), "Owner has to sign: " + tok.getOwner()))
			.requireInitialWith(UnallocatedTokensParticle.class, (tokDef, unallocated, meta) ->
					Result.of(unallocated.getTokDefRef().equals(tokDef.getRRI()), "Unallocated particles RRI must match Token RRI")
			);

		// Symbol constraints
		os.on(TokenDefinitionParticle.class)
			.require(t -> {
				final String symbol = t.getSymbol();
				if (symbol == null) {
					return Result.error("Symbol: no symbol provided.");
				}

				if (symbol.length() < MIN_SYMBOL_LENGTH || symbol.length() > MAX_SYMBOL_LENGTH) {
					return Result.error("Symbol: invalid length, must be between " + MIN_SYMBOL_LENGTH + " and "
						+ MAX_SYMBOL_LENGTH + " but is " + symbol.length());
				}

				if (!symbol.chars().allMatch(VALID_CODEPOINTS::contains)) {
					return Result.error("Symbol: can only use characters '" + VALID_SYMBOL_CHARS + "'.");
				}

				return Result.success();
			});

		// Description constraints
		os.on(TokenDefinitionParticle.class)
			.require(t -> {
				String description = t.getDescription();
				if (description == null || description.isEmpty()) {
					return Result.error("Description: no or empty provided.");
				}

				if (description.length() > MAX_DESCRIPTION_LENGTH) {
					return Result.error("Description: invalid length, description must be shorter than or equal to "
						+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
				}

				return Result.success();
			});

		// Permissions constraints
		os.on(TokenDefinitionParticle.class)
			.require(t -> {
				if (t.getTokenPermissions() == null || t.getTokenPermissions().keySet().size() != TokenTransition.values().length) {
					return Result.error(String.format("Permissions: must be set for all token transitions (%s)", Arrays.stream(TokenTransition.values())
						.map(TokenTransition::name)
						.map(String::toLowerCase)
						.collect(Collectors.joining(", "))));
				}

				return Result.success();
			});

		// Icon constraints
		os.on(TokenDefinitionParticle.class)
			.require(t -> {
				String iconUrl = t.getIconUrl();
				if (iconUrl != null && !OWASP_URL_REGEX.matcher(iconUrl).matches()) {
					return Result.error("Icon: not a valid URL: " + iconUrl);
				}

				return Result.success();
			});
	}
}
