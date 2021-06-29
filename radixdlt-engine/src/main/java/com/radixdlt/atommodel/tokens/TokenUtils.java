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

import com.radixdlt.utils.UInt256;

/**
 * Utility values and methods for tokens.
 */
public final class TokenUtils {
	/**
	 * Power of 10 number of subunits to be used by every token.
	 * Follows EIP-777 model.
	 */
	public static final int SUB_UNITS_POW_10 = 18;

	/**
	 * Implicit number of subunits to be used by every token. Follows EIP-777 model.
	 */
	public static final UInt256 SUB_UNITS = UInt256.TEN.pow(SUB_UNITS_POW_10);

	private TokenUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
