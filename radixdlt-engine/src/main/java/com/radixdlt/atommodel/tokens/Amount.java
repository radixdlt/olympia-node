/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atommodel.tokens;

import com.radixdlt.utils.UInt256;

public final class Amount {
	private final UInt256 subunits;

	private Amount(UInt256 subunits) {
		this.subunits = subunits;
	}

	public static Amount ofTokens(long units) {
		return new Amount(UInt256.from(units).multiply(TokenUtils.SUB_UNITS));
	}

	public UInt256 toSubunits() {
		return subunits;
	}
}
