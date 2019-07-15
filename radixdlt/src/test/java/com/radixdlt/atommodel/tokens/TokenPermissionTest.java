package com.radixdlt.atommodel.tokens;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import java.util.stream.Stream;
import org.junit.Test;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.AtomMetadataFromAtom;
import com.radixdlt.atomos.RRI;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class TokenPermissionTest {
	@Test
	public void when_validating_an_ok_atom_with_same_atom_only_mint_token__exception_is_not_thrown() {
		TransferrableTokensParticle particle = mock(TransferrableTokensParticle.class);
		RRI rri = mock(RRI.class);
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.particleGroups()).thenReturn(Stream.of(
			ParticleGroup.of(SpunParticle.up(particle)),
			ParticleGroup.of(SpunParticle.up(tokenDefinitionParticle))
		));

		TokenPermission.TOKEN_CREATION_ONLY.check(rri, new AtomMetadataFromAtom(atom));
	}

	@Test
	public void when_validating_an_ok_atom_with_token_owner_only_mint_token__exception_is_not_thrown() {
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		when(tokenDefinitionParticle.getOwner()).thenReturn(address);
		RRI rri = mock(RRI.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);

		AtomMetadata atomMetadata = mock(AtomMetadata.class);
		when(atomMetadata.isSignedBy(address)).thenReturn(true);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, atomMetadata);
	}

	@Test
	public void when_validating_an_atom_not_signed_by_token_owner_of_token_owner_only_mint_token__exception_is_thrown() {
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		RadixAddress address = mock(RadixAddress.class);
		when(tokenDefinitionParticle.getOwner()).thenReturn(address);
		RRI rri = mock(RRI.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(rri);
		AtomMetadata atomMetadata = mock(AtomMetadata.class);
		when(atomMetadata.isSignedBy(address)).thenReturn(false);

		TokenPermission.TOKEN_OWNER_ONLY.check(rri, atomMetadata);
	}

}