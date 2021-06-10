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
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.constraintmachine.TxnParseException;
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

	private static void validateUrl(String url) throws TxnParseException {
		if (!url.isEmpty() && !OWASP_URL_REGEX.matcher(url).matches()) {
			throw new TxnParseException("URL: not a valid URL: " + url);
		}
	}

	private static void validateIconUrl(String iconUrl) throws TxnParseException {
		if (!iconUrl.isEmpty() && !OWASP_URL_REGEX.matcher(iconUrl).matches()) {
			throw new TxnParseException("Icon: not a valid URL: " + iconUrl);
		}
	}

	static void validateDescription(String description) throws TxnParseException {
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new TxnParseException("Description: invalid length, description must be shorter than or equal to "
				+ MAX_DESCRIPTION_LENGTH + " but is " + description.length());
		}
	}

	public static void staticCheck(PreparedStake stakedParticle) throws TxnParseException {
		if (stakedParticle.getDelegateKey() == null) {
			throw new TxnParseException("delegateAddress must not be null");
		}
		if (stakedParticle.getAmount() == null) {
			throw new TxnParseException("amount must not be null");
		}
		if (stakedParticle.getAmount().isZero()) {
			throw new TxnParseException("amount must not be zero");
		}
	}

	public static void staticCheck(TokensInAccount tokensInAccount) throws TxnParseException {
		if (tokensInAccount.getAmount() == null) {
			throw new TxnParseException("amount must not be null");
		}
		if (tokensInAccount.getAmount().isZero()) {
			throw new TxnParseException("amount must not be zero");
		}
		if (!tokensInAccount.getHoldingAddr().isAccount()) {
			throw new TxnParseException("Tokens must be held by holding address: " + tokensInAccount.getHoldingAddr());
		}
	}

	public static void staticCheck(TokenResource tokenDefParticle) throws TxnParseException {
		validateDescription(tokenDefParticle.getDescription());
		validateIconUrl(tokenDefParticle.getIconUrl());
		validateUrl(tokenDefParticle.getUrl());
	}
}
