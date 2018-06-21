package com.radixdlt.client.wallet;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.identity.OneTimeUseIdentity;
import org.junit.Test;

public class TransactionAtomsTest {

	@Test
	public void testConsumerWithNoConsumable() {
		OneTimeUseIdentity radixIdentity = new OneTimeUseIdentity();
		RadixAddress address = new RadixAddress(1, radixIdentity.getPublicKey());

		/* Build atom with consumer originating from nowhere */
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100, radixIdentity.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, radixIdentity.getPublicKey().toECKeyPair(), 2, Asset.XRD.getId()))
			.buildWithPOWFee(1, radixIdentity.getPublicKey());

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(address, Asset.XRD.getId());
		transactionAtoms.accept(radixIdentity.synchronousSign(unsignedAtom).getAsTransactionAtom());
		assertEquals(0, transactionAtoms.getUnconsumedConsumables().count());
	}

	@Test
	public void testConsumerBeforeConsumable() {
		OneTimeUseIdentity radixIdentity = new OneTimeUseIdentity();
		OneTimeUseIdentity radixIdentity2 = new OneTimeUseIdentity();

		RadixAddress address = new RadixAddress(1, radixIdentity.getPublicKey());

		/* Atom with consumer originating from nowhere */
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100, radixIdentity.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, radixIdentity.getPublicKey().toECKeyPair(), 2, Asset.XRD.getId()))
			.buildWithPOWFee(1, radixIdentity.getPublicKey());

		/* Atom with consumable for previous atom's consumer */
		UnsignedAtom unsignedAtom2 = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100, radixIdentity2.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, radixIdentity.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.buildWithPOWFee(1, radixIdentity.getPublicKey());

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(address, Asset.XRD.getId());
		transactionAtoms.accept(radixIdentity.synchronousSign(unsignedAtom).getAsTransactionAtom());
		transactionAtoms.accept(radixIdentity2.synchronousSign(unsignedAtom2).getAsTransactionAtom());
		assertEquals(1, transactionAtoms.getUnconsumedConsumables().count());
	}
}