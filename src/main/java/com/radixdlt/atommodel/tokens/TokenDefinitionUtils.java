package com.radixdlt.atommodel.tokens;

import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.MAX_DESCRIPTION_LENGTH;
import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.MAX_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.MIN_SYMBOL_LENGTH;
import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.VALID_SYMBOL_CHARS;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.Result;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TokenDefinitionUtils {
	private TokenDefinitionUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static final ImmutableSet<Integer> VALID_CODEPOINTS = ImmutableSet.copyOf(VALID_SYMBOL_CHARS.chars().iterator());

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	public static Result staticCheck(MutableSupplyTokenDefinitionParticle tokenDefParticle) {
		final Result symbolResult;
		final String symbol = tokenDefParticle.getSymbol();
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
		String description = tokenDefParticle.getDescription();
		if (description == null || description.isEmpty()) {
			descriptionResult = Result.error("Description: no or empty provided.");
		} else if (description.length() > MAX_DESCRIPTION_LENGTH) {
			descriptionResult = Result.error("Description: invalid length, description must be shorter than or equal to "
				+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
		} else {
			descriptionResult = Result.success();
		}

		final Result permissionsResult;
		if (tokenDefParticle.getTokenPermissions() == null || tokenDefParticle.getTokenPermissions().keySet().size() != TokenTransition.values().length) {
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
		String iconUrl = tokenDefParticle.getIconUrl();
		if (iconUrl != null && !OWASP_URL_REGEX.matcher(iconUrl).matches()) {
			iconResult = Result.error("Icon: not a valid URL: " + iconUrl);
		} else {
			iconResult = Result.success();
		}

		return Result.combine(symbolResult, descriptionResult, permissionsResult, iconResult);
	}
}
