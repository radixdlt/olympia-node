package com.radixdlt.client.application.translate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.ParticleStore;
import java.util.Collections;
import org.junit.Test;

public class TokenTransferTranslatorTest {
	@Test
	public void testSendToSelfTest() {
		RadixUniverse universe = mock(RadixUniverse.class);
		ParticleStore particleStore = mock(ParticleStore.class);
		TransactionAtom atom = mock(TransactionAtom.class);
		ECPublicKey myKey = mock(ECPublicKey.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		when(universe.getAddressFrom(myKey)).thenReturn(myAddress);
		when(atom.summary()).thenReturn(Collections.singletonMap(
			Collections.singleton(myKey), Collections.singletonMap(Asset.TEST.getId(), 0L)
		));

		TokenTransferTranslator tokenTransferTranslator = new TokenTransferTranslator(universe, particleStore);
		TokenTransfer tokenTransfer = tokenTransferTranslator.fromAtom(atom);
		assertEquals(myAddress, tokenTransfer.getFrom());
		assertEquals(myAddress, tokenTransfer.getTo());
	}
}