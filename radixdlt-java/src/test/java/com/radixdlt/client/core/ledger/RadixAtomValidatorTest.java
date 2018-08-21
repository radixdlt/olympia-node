package com.radixdlt.client.core.ledger;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixAtomValidatorTest {

	@Test(expected = AtomValidationException.class)
	public void testSignatureValidation() throws AtomValidationException {
		RadixHash hash = mock(RadixHash.class);

		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		when(keyPair.getUID()).thenReturn(new EUID(1));
		when(keyPair.getPublicKey()).thenReturn(publicKey);
		when(publicKey.getUID()).thenReturn(new EUID(1));

		Consumer consumer = mock(Consumer.class);
		when(consumer.isAbstractConsumable()).thenReturn(true);
		when(consumer.getAsAbstractConsumable()).thenReturn(consumer);
		when(consumer.getOwnersPublicKeys()).thenReturn(Collections.singleton(publicKey));
		when(consumer.getAssetId()).thenReturn(Asset.XRD.getId());

		TransactionAtom atom = mock(TransactionAtom.class);
		when(atom.getHash()).thenReturn(hash);
		when(atom.getSignature(any())).thenReturn(Optional.empty());
		when(atom.getParticles()).thenReturn(Arrays.asList(consumer));

		RadixAtomValidator validator = RadixAtomValidator.getInstance();
		validator.validateSignatures(atom);
	}

	@Test
	public void testPayloadValidationWithNoSignatures() throws AtomValidationException {
		RadixHash hash = mock(RadixHash.class);

		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		when(keyPair.getUID()).thenReturn(new EUID(1));
		when(keyPair.getPublicKey()).thenReturn(publicKey);
		when(publicKey.getUID()).thenReturn(new EUID(1));

		Consumer consumer = mock(Consumer.class);
		when(consumer.isAbstractConsumable()).thenReturn(true);
		when(consumer.getAsAbstractConsumable()).thenReturn(consumer);
		when(consumer.getOwnersPublicKeys()).thenReturn(Collections.singleton(publicKey));
		when(consumer.getAssetId()).thenReturn(Asset.XRD.getId());

		TransactionAtom atom = mock(TransactionAtom.class);
		when(atom.getHash()).thenReturn(hash);
		when(atom.getSignature(any())).thenReturn(Optional.empty());

		RadixAtomValidator validator = RadixAtomValidator.getInstance();
		validator.validateSignatures(atom);
	}
}