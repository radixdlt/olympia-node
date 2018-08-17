package com.radixdlt.client.dapps.wallet;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Arrays;
import org.junit.Test;

public class WalletTransactionTest {
	@Test
	public void getAmountTest() {
		RadixAddress address = mock(RadixAddress.class);
		TransactionAtom atom = mock(TransactionAtom.class);
		ECKeyPair ecKeyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		when(ecKeyPair.getPublicKey()).thenReturn(publicKey);
		when(address.ownsKey(any(ECKeyPair.class))).thenReturn(true);
		when(address.ownsKey(any(ECPublicKey.class))).thenReturn(true);
		Consumable consumable1 = new Consumable(10, ecKeyPair, 0, Asset.XRD.getId());
		Consumable consumable2 = new Consumable(10, ecKeyPair, 1, Asset.XRD.getId());
		when(atom.getParticles()).thenReturn(Arrays.asList(consumable1, consumable2));

		WalletTransaction transaction = new WalletTransaction(address, atom);
		assertEquals(20, transaction.getAmount());
	}
}