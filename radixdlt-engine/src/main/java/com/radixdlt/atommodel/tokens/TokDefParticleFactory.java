/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.atomos.RriId;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

/**
 * Factory to more easily create token particles given a token definition.
 */
public final class TokDefParticleFactory {
	private final RriId tokDefRef;
	private final boolean isMutable;

	private TokDefParticleFactory(
		RriId tokDefRef,
		boolean isMutable
	) {
		this.tokDefRef = tokDefRef;
		this.isMutable = isMutable;
	}

	public TokensParticle createTransferrable(RadixAddress address, UInt256 amount) {
		return new TokensParticle(
			address,
			amount,
			tokDefRef
		);
	}

	public static TokDefParticleFactory create(
		RriId tokDefRef,
		boolean isMutable
	) {
		Objects.requireNonNull(tokDefRef);

		return new TokDefParticleFactory(tokDefRef, isMutable);
	}
}
