package com.radixdlt.atommodel.tokens;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TokenDefinitionUtils {
	/**
	 * Power of 10 number of subunits to be used by every token.
	 * Follows EIP-777 model.
	 */
	public static final int SUB_UNITS_POW_10 = 18;

	/**
	 * Implicit number of subunits to be used by every token. Follows EIP-777 model.
	 */
	public static final UInt256 SUB_UNITS = UInt256.TEN.pow(SUB_UNITS_POW_10);

	public static final int MIN_SYMBOL_LENGTH = 1;
	public static final int MAX_SYMBOL_LENGTH = 14;
	public static final String VALID_SYMBOL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	public static final int MAX_DESCRIPTION_LENGTH = 200;
	public static final int MAX_ICON_DIMENSION = 32;

	private TokenDefinitionUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static final ImmutableSet<Integer> VALID_CODEPOINTS = ImmutableSet.copyOf(VALID_SYMBOL_CHARS.chars().iterator());

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	private static Result validateIconUrl(String iconUrl) {
		if (iconUrl != null && !OWASP_URL_REGEX.matcher(iconUrl).matches()) {
			return Result.error("Icon: not a valid URL: " + iconUrl);
		}
		return Result.success();
	}

	static Result validateSymbol(String symbol) {
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
	}

	static Result validateDescription(String description) {
		if (description == null || description.isEmpty()) {
			return Result.error("Description: no or empty provided.");
		}

		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			return Result.error("Description: invalid length, description must be shorter than or equal to "
				+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
		}

		return Result.success();
	}

	public static Result staticCheck(FixedSupplyTokenDefinitionParticle tokenDefParticle) {
		final Result symbolResult = validateSymbol(tokenDefParticle.getSymbol());
		if (symbolResult.isError()) {
			return symbolResult;
		}

		final Result descriptionResult = validateDescription(tokenDefParticle.getDescription());
		if (descriptionResult.isError()) {
			return descriptionResult;
		}

		final Result iconResult = validateIconUrl(tokenDefParticle.getIconUrl());
		if (iconResult.isError()) {
			return iconResult;
		}

		return Result.success();
	}

	public static Result staticCheck(MutableSupplyTokenDefinitionParticle tokenDefParticle) {
		final Result symbolResult = validateSymbol(tokenDefParticle.getSymbol());
		if (symbolResult.isError()) {
			return symbolResult;
		}

		final Result descriptionResult = validateDescription(tokenDefParticle.getDescription());
		if (descriptionResult.isError()) {
			return descriptionResult;
		}

		if (tokenDefParticle.getTokenPermissions() == null
			|| tokenDefParticle.getTokenPermissions().size() != TokenTransition.values().length) {
			return Result.error(
				String.format(
					"Permissions: must be set for all token transitions (%s)",
					Arrays.stream(TokenTransition.values())
						.map(TokenTransition::name)
						.map(String::toLowerCase)
						.collect(Collectors.joining(", "))
				)
			);
		}

		final Result iconResult = validateIconUrl(tokenDefParticle.getIconUrl());
		if (iconResult.isError()) {
			return iconResult;
		}

		return Result.success();
	}
}
