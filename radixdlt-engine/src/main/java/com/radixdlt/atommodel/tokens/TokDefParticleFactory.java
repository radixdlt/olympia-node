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

import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.Objects;

/**
 * Factory to more easily create token particles given a token definition.
 */
public final class TokDefParticleFactory {
	private final RRI tokDefRef;
	private final Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions;
	private final UInt256 granularity;

	private TokDefParticleFactory(
		RRI tokDefRef,
		Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions,
		UInt256 granularity
	) {
		this.tokDefRef = tokDefRef;
		this.tokenPermissions = tokenPermissions;
		this.granularity = granularity;
	}

	public UnallocatedTokensParticle createUnallocated(UInt256 amount) {
		return new UnallocatedTokensParticle(
			amount,
			granularity,
			tokDefRef,
			tokenPermissions
		);
	}

	public TransferrableTokensParticle createTransferrable(RadixAddress address, UInt256 amount) {
		return new TransferrableTokensParticle(
			address,
			amount,
			granularity,
			tokDefRef,
			tokenPermissions
		);
	}

	public StakedTokensParticle createStaked(RadixAddress delegate, RadixAddress address, UInt256 amount) {
		return new StakedTokensParticle(
			delegate,
			address,
			amount,
			granularity,
			tokDefRef,
			tokenPermissions
		);
	}

	public static TokDefParticleFactory create(
		RRI tokDefRef,
		Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions,
		UInt256 granularity
	) {
		Objects.requireNonNull(tokDefRef);
		Objects.requireNonNull(tokenPermissions);
		Objects.requireNonNull(granularity);

		return new TokDefParticleFactory(tokDefRef, tokenPermissions, granularity);
	}

	public static TokDefParticleFactory createFrom(TransferrableTokensParticle particle) {
		return new TokDefParticleFactory(particle.getTokDefRef(), particle.getTokenPermissions(), particle.getGranularity());
	}
}
