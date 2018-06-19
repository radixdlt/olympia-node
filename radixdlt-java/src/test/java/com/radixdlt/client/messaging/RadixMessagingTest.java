package com.radixdlt.client.messaging;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.identity.OneTimeUseIdentity;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import org.junit.Test;

public class RadixMessagingTest {

	@Test
	public void testReceiveUndecryptableMessage() {
		RadixIdentity myIdentity = new OneTimeUseIdentity()	;
		ECKeyPair unknownKey = ECKeyPairGenerator.generateKeyPair();
		Observer observer = mock(Observer.class);

		Atom undecryptableAtom = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId(RadixMessaging.APPLICATION_ID)
			.payload(new RadixMessageContent(null, null, "Hello").toJson())
			.addProtector(unknownKey.getPublicKey())
			.build()
			.getRawAtom();

		Atom decryptableAtom = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId(RadixMessaging.APPLICATION_ID)
			.payload(new RadixMessageContent(null, null, "Hello").toJson())
			.addProtector(myIdentity.getPublicKey())
			.build()
			.getRawAtom();

		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.just(undecryptableAtom, decryptableAtom));
		RadixMessaging messaging = new RadixMessaging(universe);
		messaging.getAllMessagesDecrypted(myIdentity)
			.subscribe(observer);

		verify(observer, times(1)).onNext(any());
		verify(observer, times(0)).onError(any());
	}

	@Test
	public void testBadMessage() {
		RadixIdentity myIdentity = new OneTimeUseIdentity()	;
		Observer observer = mock(Observer.class);

		Atom undecryptableAtom = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId(RadixMessaging.APPLICATION_ID)
			.payload("hello")
			.addProtector(myIdentity.getPublicKey())
			.build()
			.getRawAtom();

		Atom decryptableAtom = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId(RadixMessaging.APPLICATION_ID)
			.payload(new RadixMessageContent(null, null, "Hello").toJson())
			.addProtector(myIdentity.getPublicKey())
			.build()
			.getRawAtom();

		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.just(undecryptableAtom, decryptableAtom));
		RadixMessaging messaging = new RadixMessaging(universe);
		messaging.getAllMessagesDecrypted(myIdentity)
			.subscribe(observer);

		verify(observer, times(1)).onNext(any());
		verify(observer, times(0)).onError(any());
	}
}