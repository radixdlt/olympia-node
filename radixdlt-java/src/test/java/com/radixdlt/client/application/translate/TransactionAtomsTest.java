package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.PayloadAtom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.observers.TestObserver;
import java.util.Collection;
import org.junit.Test;

public class TransactionAtomsTest {

	@Test
	public void testConsumerWithNoConsumable() {
		ECKeyPair keyPair = new ECKeyPair(new ECPublicKey(new byte[33]));
		RadixAddress address = mock(RadixAddress.class);
		when(address.ownsKey(any(ECKeyPair.class))).thenReturn(true);
		when(address.ownsKey(any(ECPublicKey.class))).thenReturn(true);

		/* Build atom with consumer originating from nowhere */
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(PayloadAtom.class)
			.addParticle(new Consumer(100, keyPair, 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, keyPair, 2, Asset.XRD.getId()))
			.build();

		TestObserver<Collection<Consumable>> observer = TestObserver.create();

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(address, Asset.XRD.getId());
		transactionAtoms.accept(unsignedAtom.getRawAtom().getAsTransactionAtom())
			.getUnconsumedConsumables().subscribe(observer);
		observer.assertValueCount(0);
	}

	@Test
	public void testConsumerBeforeConsumable() {
		ECPublicKey publicKey = new ECPublicKey(new byte[33]);
		ECKeyPair keyPair = new ECKeyPair(publicKey);

		byte[] otherRaw = new byte[33];
		otherRaw[0] = 1;
		ECPublicKey otherPublicKey = new ECPublicKey(otherRaw);
		ECKeyPair otherKeyPair = new ECKeyPair(otherPublicKey);

		RadixAddress address = mock(RadixAddress.class);
		when(address.ownsKey(eq(keyPair))).thenReturn(true);
		when(address.ownsKey(eq(otherKeyPair))).thenReturn(false);
		when(address.ownsKey(eq(publicKey))).thenReturn(true);
		when(address.ownsKey(eq(otherPublicKey))).thenReturn(false);

		/* Atom with consumer originating from nowhere */
		UnsignedAtom unsignedAtom = new AtomBuilder()
			.type(PayloadAtom.class)
			.addParticle(new Consumer(100, keyPair, 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, keyPair, 2, Asset.XRD.getId()))
			.build();

		/* Atom with consumable for previous atom's consumer */
		UnsignedAtom unsignedAtom2 = new AtomBuilder()
			.type(PayloadAtom.class)
			.addParticle(new Consumer(100, otherKeyPair, 1, Asset.XRD.getId()))
			.addParticle(new Consumable(100, keyPair, 1, Asset.XRD.getId()))
			.build();

		TestObserver<Collection<Consumable>> observer = TestObserver.create();

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(address, Asset.XRD.getId());
		transactionAtoms.accept(unsignedAtom.getRawAtom().getAsTransactionAtom());
		transactionAtoms.accept(unsignedAtom2.getRawAtom().getAsTransactionAtom())
			.getUnconsumedConsumables()
			.subscribe(observer);

		observer.assertValue(collection -> collection.stream().findFirst().get().getNonce() == 2);
	}
}
