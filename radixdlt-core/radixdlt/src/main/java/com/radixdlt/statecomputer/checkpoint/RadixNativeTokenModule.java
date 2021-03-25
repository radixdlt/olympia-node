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

package com.radixdlt.statecomputer.checkpoint;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.atom.TokenDefinition;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.fees.NativeToken;

/**
 * Specifies token definition for the Radix Native Token, XRD
 */
public class RadixNativeTokenModule extends AbstractModule {
	private static final String RADIX_ICON_URL  = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";
	private static final String RADIX_TOKEN_URL = "https://tokens.radixdlt.com/";
	private static final ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> XRD_TOKEN_PERMISSIONS =
		ImmutableMap.of(
			MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
			MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
		);

	@Provides
	@NativeToken
	TokenDefinition tokenDefinition() {
		return new TokenDefinition(
			TokenDefinitionUtils.getNativeTokenShortCode(),
			"Rads",
			"Radix Native Tokens",
			RADIX_ICON_URL,
			RADIX_TOKEN_URL,
			XRD_TOKEN_PERMISSIONS
		);
	}
}
