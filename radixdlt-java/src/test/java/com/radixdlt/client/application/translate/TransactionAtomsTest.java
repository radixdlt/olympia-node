package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.observers.TestObserver;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;

public class TransactionAtomsTest {

	@Test
	public void testConsumerWithNoConsumable() {
		AccountReference accountReference = mock(AccountReference.class);
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		when(accountReference.getKey()).thenReturn(ecPublicKey);

		RadixAddress address = mock(RadixAddress.class);
		when(address.ownsKey(any(ECKeyPair.class))).thenReturn(true);
		when(address.ownsKey(any(ECPublicKey.class))).thenReturn(true);

		Consumable consumer = mock(Consumable.class);
		when(consumer.getTokenClass()).thenReturn(Token.TEST.getId());
		when(consumer.getOwnersPublicKeys()).thenReturn(Collections.singleton(ecPublicKey));
		when(consumer.getDson()).thenReturn(new byte[] {0});

		Consumable consumable = mock(Consumable.class);
		when(consumable.getTokenClass()).thenReturn(Token.TEST.getId());
		when(consumable.getOwnersPublicKeys()).thenReturn(Collections.singleton(ecPublicKey));
		when(consumable.getDson()).thenReturn(new byte[] {1});

		// Build atom with consumer originating from nowhere
		Atom atom = mock(Atom.class);
		when(atom.getConsumables(Spin.DOWN)).thenReturn(Collections.singletonList(consumer));
		when(atom.getConsumables(Spin.UP)).thenReturn(Collections.singletonList(consumable));

		// Make sure we don't count it unless we find the matching consumable
		TransactionAtoms transactionAtoms = new TransactionAtoms(address, Token.TEST.getId());

		TestObserver<Collection<Consumable>> observer = TestObserver.create();
		transactionAtoms.accept(atom).getUnconsumedConsumables().subscribe(observer);
		observer.assertValueCount(0);
	}

	@Test
	public void testConsumerBeforeConsumable() {
		AccountReference accountReference = mock(AccountReference.class);
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		when(accountReference.getKey()).thenReturn(ecPublicKey);

		RadixAddress address = mock(RadixAddress.class);
		when(address.ownsKey(ecPublicKey)).thenReturn(true);

		Consumable consumer = mock(Consumable.class);
		when(consumer.getSpin()).thenReturn(Spin.DOWN);
		when(consumer.getTokenClass()).thenReturn(Token.TEST.getId());
		when(consumer.getOwnersPublicKeys()).thenReturn(Collections.singleton(ecPublicKey));
		when(consumer.getDson()).thenReturn(new byte[] {0});

		Consumable consumable = mock(Consumable.class);
		when(consumable.getSpin()).thenReturn(Spin.UP);
		when(consumable.getTokenClass()).thenReturn(Token.TEST.getId());
		when(consumable.getOwnersPublicKeys()).thenReturn(Collections.singleton(ecPublicKey));
		when(consumable.getDson()).thenReturn(new byte[] {1});

		Atom atom = mock(Atom.class);
		when(atom.getConsumables(Spin.DOWN)).thenReturn(Collections.singletonList(consumer));
		when(atom.getConsumables(Spin.UP)).thenReturn(Collections.singletonList(consumable));

		Consumable oldConsumable = mock(Consumable.class);
		when(oldConsumable.getSpin()).thenReturn(Spin.UP);
		when(oldConsumable.getTokenClass()).thenReturn(Token.TEST.getId());
		when(oldConsumable.getOwnersPublicKeys()).thenReturn(Collections.singleton(ecPublicKey));
		when(oldConsumable.getDson()).thenReturn(new byte[] {0});

		Consumable oldConsumer = mock(Consumable.class);
		when(oldConsumer.getSpin()).thenReturn(Spin.DOWN);
		when(oldConsumer.getTokenClass()).thenReturn(Token.TEST.getId());
		when(oldConsumer.getOwnersPublicKeys()).thenReturn(Collections.singleton(mock(ECPublicKey.class)));
		when(oldConsumer.getDson()).thenReturn(new byte[] {2});

		Atom oldAtom = mock(Atom.class);
		when(oldAtom.getConsumables(Spin.DOWN)).thenReturn(Collections.singletonList(oldConsumer));
		when(oldAtom.getConsumables(Spin.UP)).thenReturn(Collections.singletonList(oldConsumable));

		TestObserver<Collection<Consumable>> observer = TestObserver.create();

		/* Make sure we don't count it unless we find the matching consumable */
		TransactionAtoms transactionAtoms = new TransactionAtoms(address, Token.TEST.getId());
		transactionAtoms.accept(atom);
		transactionAtoms.accept(oldAtom)
			.getUnconsumedConsumables()
			.subscribe(observer);

		observer.assertValue(collection -> collection.stream().findFirst().get().getDson()[0] == 1);
	}
}
