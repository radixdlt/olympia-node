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

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Test;

public class TokenPermissionTest {
	@Test
	public void when_validating_an_ok_atom_with_token_owner_only_mint_token__exception_is_not_thrown() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getPublicKey()).thenReturn(key);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);

		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(key)).thenReturn(true);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, witnessData);
	}

	@Test
	public void when_validating_an_atom_not_signed_by_token_owner_of_token_owner_only_mint_token__exception_is_thrown() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getPublicKey()).thenReturn(key);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);
		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(key)).thenReturn(false);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, witnessData);
	}

}