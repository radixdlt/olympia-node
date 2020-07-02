/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.tokens;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility values and methods for tokens.
 */
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

	/**
	 * Returns the short code of the native asset of the network this node is a part of.
	 *
	 * @return The short code of the native asset.
	 */
	public static String getNativeTokenShortCode() {
		return "XRD";
	}

	private static Result validateUrl(String url) {
		if (url != null && !OWASP_URL_REGEX.matcher(url).matches()) {
			return Result.error("URL: not a valid URL: " + url);
		}
		return Result.success();
	}

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
		if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
			return Result.error("Description: invalid length, description must be shorter than or equal to "
				+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
		}

		return Result.success();
	}

	public static Result staticCheck(StakedTokensParticle stakedParticle) {
		if (stakedParticle.getDelegateAddress() == null) {
			return Result.error("delegateAddress must not be null");
		}
		if (stakedParticle.getAmount() == null) {
			return Result.error("amount must not be null");
		}
		if (stakedParticle.getAmount().isZero()) {
			return Result.error("amount must not be zero");
		}
		if (stakedParticle.getGranularity() == null) {
			return Result.error("granularity must not be null");
		}
		if (stakedParticle.getGranularity().isZero() || !stakedParticle.getAmount().remainder(stakedParticle.getGranularity()).isZero()) {
			return Result.error("amount " + stakedParticle.getAmount() + " does not fit granularity " + stakedParticle.getGranularity());
		}

		return Result.success();
	}

	public static Result staticCheck(TransferrableTokensParticle tokensParticle) {
		if (tokensParticle.getAmount() == null) {
			return Result.error("amount must not be null");
		}
		if (tokensParticle.getAmount().isZero()) {
			return Result.error("amount must not be zero");
		}
		if (tokensParticle.getGranularity() == null) {
			return Result.error("granularity must not be null");
		}
		if (tokensParticle.getGranularity().isZero() || !tokensParticle.getAmount().remainder(tokensParticle.getGranularity()).isZero()) {
			return Result.error("amount " + tokensParticle.getAmount() + " does not fit granularity " + tokensParticle.getGranularity());
		}

		return Result.success();
	}

	public static Result staticCheck(UnallocatedTokensParticle particle) {
		if (particle.getAmount() == null) {
			return Result.error("amount must not be null");
		}
		if (particle.getAmount().isZero()) {
			return Result.error("amount cannot be zero");
		}
		if (particle.getGranularity() == null) {
			return Result.error("granularity must not be null");
		}
		if (particle.getGranularity().isZero()) {
			return Result.error("granularity must not be zero");
		}

		return Result.success();
	}


	public static Result staticCheck(FixedSupplyTokenDefinitionParticle tokenDefParticle) {
		final Result symbolResult = validateSymbol(tokenDefParticle.getRRI().getName());
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

		final Result urlResult = validateUrl(tokenDefParticle.getUrl());
		if (urlResult.isError()) {
			return urlResult;
		}

		return Result.success();
	}

	public static Result staticCheck(MutableSupplyTokenDefinitionParticle tokenDefParticle) {
		final Result symbolResult = validateSymbol(tokenDefParticle.getRRI().getName());
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
