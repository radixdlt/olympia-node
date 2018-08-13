package com.radixdlt.client.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

public class RadixMessagingTest {

	@Test
	public void testReceiveUndecryptableMessage() {
		ECPublicKey key = mock(ECPublicKey.class);
		RadixIdentity myIdentity = mock(RadixIdentity.class);
		when(myIdentity.getPublicKey()).thenReturn(key);
		TestObserver<RadixMessage> observer = TestObserver.create();

		ApplicationPayloadAtom undecryptableAtom = mock(ApplicationPayloadAtom.class);
		when(undecryptableAtom.getApplicationId()).thenReturn(RadixMessaging.APPLICATION_ID);

		ApplicationPayloadAtom decryptableAtom = mock(ApplicationPayloadAtom.class);
		when(decryptableAtom.getApplicationId()).thenReturn(RadixMessaging.APPLICATION_ID);

		when(myIdentity.decrypt(any()))
			.thenReturn(Single.error(new CryptoException("Can't decrypt")))
			.thenReturn(Single.just(new RadixMessageContent(null, null, "Hello").toJson().getBytes()));

		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.just(undecryptableAtom, decryptableAtom));

		RadixMessaging messaging = new RadixMessaging(myIdentity, universe);
		messaging.getAllMessages()
			.subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}

	@Test
	public void testBadMessage() {
		ECPublicKey key = mock(ECPublicKey.class);
		RadixIdentity myIdentity = mock(RadixIdentity.class);
		when(myIdentity.getPublicKey()).thenReturn(key);
		TestObserver<RadixMessage> observer = TestObserver.create();

		ApplicationPayloadAtom undecryptableAtom = mock(ApplicationPayloadAtom.class);
		when(undecryptableAtom.getApplicationId()).thenReturn(RadixMessaging.APPLICATION_ID);

		ApplicationPayloadAtom decryptableAtom = mock(ApplicationPayloadAtom.class);
		when(decryptableAtom.getApplicationId()).thenReturn(RadixMessaging.APPLICATION_ID);

		when(myIdentity.decrypt(any()))
			.thenReturn(Single.just(new byte[] {0, 1, 2, 3}))
			.thenReturn(Single.just(new RadixMessageContent(null, null, "Hello").toJson().getBytes()));

		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.just(undecryptableAtom, decryptableAtom));

		RadixMessaging messaging = new RadixMessaging(myIdentity, universe);
		messaging.getAllMessages()
			.subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}
}
