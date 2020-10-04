/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.middleware2;

import com.google.common.hash.HashCode;
import com.google.inject.name.Named;
import com.radixdlt.utils.POW;
import javax.inject.Inject;

/**
 * Temporary (will be removing pow fees soon) class for computing pow spent in an atom
 */
public final class PowFeeComputer {
	private final int magic;

	@Inject
	public PowFeeComputer(@Named("magic") int magic) {
		this.magic = magic;
	}

	public HashCode computePowSpent(LedgerAtom ledgerAtom, long powNonce) {
		final HashCode powFeeHash = ledgerAtom.getPowFeeHash();
		POW pow = new POW(this.magic, powFeeHash, powNonce);
		return pow.getHash();
	}
}
