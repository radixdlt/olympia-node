package com.radixdlt.client.wallet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.identity.OneTimeUseIdentity;
import org.junit.Test;

public class TransactionAtomsTest {

	@Test
	public void testConsumerWithNoConsumable() {
		OneTimeUseIdentity radixIdentity = new OneTimeUseIdentity();

		/* Build atom with consumer originating from nowhere */
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100, radixIdentity.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, radixIdentity.getPublicKey().toECKeyPair(), 2, Asset.XRD.getId()))
			.buildWithPOWFee(RadixUniverse.getInstance().getLedger().getMagic(), radixIdentity.getPublicKey());

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(RadixUniverse.getInstance().getAddressFrom(radixIdentity.getPublicKey()), Asset.XRD.getId());
		transactionAtoms.accept(radixIdentity.synchronousSign(unsignedAtom).getAsTransactionAtom());
		assertEquals(0, transactionAtoms.getUnconsumedConsumables().count());
	}

	@Test
	public void testConsumerBeforeConsumable() {
		OneTimeUseIdentity radixIdentity = new OneTimeUseIdentity();
		OneTimeUseIdentity radixIdentity2 = new OneTimeUseIdentity();

		/* Build atom with consumer originating from nowhere */
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100, radixIdentity.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, radixIdentity.getPublicKey().toECKeyPair(), 2, Asset.XRD.getId()))
			.buildWithPOWFee(RadixUniverse.getInstance().getLedger().getMagic(), radixIdentity.getPublicKey());

		UnsignedAtom unsignedAtom2 = new AtomBuilder()
			.type(TransactionAtom.class)
			.addParticle(new Consumer(100, radixIdentity2.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, radixIdentity.getPublicKey().toECKeyPair(), 1, Asset.XRD.getId()))
			.buildWithPOWFee(RadixUniverse.getInstance().getLedger().getMagic(), radixIdentity.getPublicKey());

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(RadixUniverse.getInstance().getAddressFrom(radixIdentity.getPublicKey()), Asset.XRD.getId());
		transactionAtoms.accept(radixIdentity.synchronousSign(unsignedAtom).getAsTransactionAtom());
		transactionAtoms.accept(radixIdentity2.synchronousSign(unsignedAtom2).getAsTransactionAtom());
		assertEquals(1, transactionAtoms.getUnconsumedConsumables().count());
	}
}