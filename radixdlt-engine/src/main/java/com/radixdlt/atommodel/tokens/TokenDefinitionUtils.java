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
import com.radixdlt.atomos.Result;
import com.radixdlt.utils.UInt256;

import java.util.regex.Pattern;

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

	private TokenDefinitionUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static final ImmutableSet<Integer> VALID_CODEPOINTS = ImmutableSet.copyOf(VALID_SYMBOL_CHARS.chars().iterator());

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))"
		+ "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	/**
	 * Returns the short code of the native asset of the network this node is a part of.
	 *
	 * @return The short code of the native asset.
	 */
	public static String getNativeTokenShortCode() {
		return "xrd";
	}

	private static Result validateUrl(String url) {
		if (!url.isEmpty() && !OWASP_URL_REGEX.matcher(url).matches()) {
			return Result.error("URL: not a valid URL: " + url);
		}
		return Result.success();
	}

	private static Result validateIconUrl(String iconUrl) {
		if (!iconUrl.isEmpty() && !OWASP_URL_REGEX.matcher(iconUrl).matches()) {
			return Result.error("Icon: not a valid URL: " + iconUrl);
		}
		return Result.success();
	}

	static Result validateDescription(String description) {
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			return Result.error("Description: invalid length, description must be shorter than or equal to "
				+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
		}

		return Result.success();
	}

	public static Result staticCheck(StakedTokensParticle stakedParticle) {
		if (stakedParticle.getDelegateKey() == null) {
			return Result.error("delegateAddress must not be null");
		}
		if (stakedParticle.getAmount() == null) {
			return Result.error("amount must not be null");
		}
		if (stakedParticle.getAmount().isZero()) {
			return Result.error("amount must not be zero");
		}

		return Result.success();
	}

	public static Result staticCheck(TokensParticle tokensParticle) {
		if (tokensParticle.getAmount() == null) {
			return Result.error("amount must not be null");
		}
		if (tokensParticle.getAmount().isZero()) {
			return Result.error("amount must not be zero");
		}
		if (!tokensParticle.getHoldingAddr().isAccount()) {
			return Result.error("Tokens must be held by holding address: " + tokensParticle.getHoldingAddr());
		}

		return Result.success();
	}

	public static Result staticCheck(TokenDefinitionParticle tokenDefParticle) {
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
}
