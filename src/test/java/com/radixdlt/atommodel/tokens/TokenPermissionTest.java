package com.radixdlt.atommodel.tokens;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.AtomMetadata;
import org.junit.Test;

public class TokenPermissionTest {
	@Test
	public void when_validating_an_ok_atom_with_token_owner_only_mint_token__exception_is_not_thrown() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);

		AtomMetadata atomMetadata = mock(AtomMetadata.class);
		when(atomMetadata.isSignedBy(address)).thenReturn(true);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, atomMetadata);
	}

	@Test
	public void when_validating_an_atom_not_signed_by_token_owner_of_token_owner_only_mint_token__exception_is_thrown() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);
		AtomMetadata atomMetadata = mock(AtomMetadata.class);
		when(atomMetadata.isSignedBy(address)).thenReturn(false);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, atomMetadata);
	}

}