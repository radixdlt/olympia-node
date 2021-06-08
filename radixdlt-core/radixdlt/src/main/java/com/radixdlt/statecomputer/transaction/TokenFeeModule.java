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

package com.radixdlt.statecomputer.transaction;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.engine.PostParsedChecker;
import com.radixdlt.fees.FeeEntry;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.fees.PerBytesFeeEntry;
import com.radixdlt.fees.PerParticleFeeEntry;
import com.radixdlt.utils.UInt256;

/**
 * Module which provides a token fee checker
 */
public class TokenFeeModule extends AbstractModule {

	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), PostParsedChecker.class)
			.addBinding().to(TokenFeeChecker.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton // Immutable and stateless, but no point having more than one
	private FeeTable feeTable() {
		// WARNING: There is a duplicate fee table in RadixUniverse in the Java library.
		// Possibly also one somewhere in the Javascript library too.
		// If you update this fee table, you will need to change the ones there also.
		ImmutableList<FeeEntry> feeEntries = ImmutableList.of(
			// 1 millirad per byte after the first three kilobytes
			PerBytesFeeEntry.of(1,  3072, milliRads(1L)),
			// 1,000 millirads per fixed supply token definition
			PerParticleFeeEntry.of(TokenResource.class, 0, milliRads(1000L))
		);

		// Minimum fee of 40 millirads
		return FeeTable.from(milliRads(40L), feeEntries);
	}

	private static UInt256 milliRads(long count) {
		// 1 count is 10^{-3} rads, so we subtract that from the sub-units power
		// No risk of overflow here, as 10^18 is approx 60 bits, plus 64 bits of count will not exceed 256 bits
		return UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(count));
	}
}
