package com.radixdlt.atommodel.tokens;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Test;

public class TokenPermissionTest {
	@Test
	public void when_validating_an_ok_atom_with_token_owner_only_mint_token__exception_is_not_thrown() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getKey()).thenReturn(key);
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
		when(address.getKey()).thenReturn(key);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);
		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(key)).thenReturn(false);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, witnessData);
	}

}