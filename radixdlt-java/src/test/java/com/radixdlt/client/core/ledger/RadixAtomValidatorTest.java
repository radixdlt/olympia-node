package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixAtomValidatorTest {

	@Test
	public void testSignatureValidation() {
		RadixHash hash = mock(RadixHash.class);

		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		when(keyPair.getUID()).thenReturn(new EUID(1));
		when(keyPair.getPublicKey()).thenReturn(publicKey);
		when(publicKey.getUID()).thenReturn(new EUID(1));

		Consumable consumer = mock(Consumable.class);
		when(consumer.getOwnersPublicKeys()).thenReturn(Collections.singleton(publicKey));
		TokenRef token = mock(TokenRef.class);
		when(token.getIso()).thenReturn("TEST");
		when(consumer.getTokenRef()).thenReturn(token);

		Atom atom = mock(Atom.class);
		when(atom.getHash()).thenReturn(hash);
		when(atom.getSignature(any())).thenReturn(Optional.empty());
		when(atom.getConsumables(Spin.DOWN)).thenReturn(Arrays.asList(consumer));

		RadixAtomValidator validator = RadixAtomValidator.getInstance();
		assertThatThrownBy(() -> validator.validateSignatures(atom))
			.isInstanceOf(AtomValidationException.class);
	}

	@Test
	public void testPayloadValidationWithNoSignatures() throws AtomValidationException {
		RadixHash hash = mock(RadixHash.class);

		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		when(keyPair.getUID()).thenReturn(new EUID(1));
		when(keyPair.getPublicKey()).thenReturn(publicKey);
		when(publicKey.getUID()).thenReturn(new EUID(1));

		Atom atom = mock(Atom.class);
		when(atom.getHash()).thenReturn(hash);
		when(atom.getSignature(any())).thenReturn(Optional.empty());

		RadixAtomValidator validator = RadixAtomValidator.getInstance();
		validator.validateSignatures(atom);
	}
}