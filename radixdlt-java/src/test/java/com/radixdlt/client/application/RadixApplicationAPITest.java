package com.radixdlt.client.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import org.junit.Test;

public class RadixApplicationAPITest {

	@Test
	public void testUndecryptableData() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixLedger ledger = mock(RadixLedger.class);
		RadixAddress address = mock(RadixAddress.class);

		when(identity.decrypt(any()))
			.thenReturn(Single.error(new CryptoException("Can't decrypt")))
			.thenReturn(Single.just(new byte[] {0}));

		Encryptor encryptor = mock(Encryptor.class);
		EncryptedPrivateKey protector = mock(EncryptedPrivateKey.class);
		when(encryptor.getProtectors()).thenReturn(Collections.singletonList(protector));

		Payload payload = mock(Payload.class);

		ApplicationPayloadAtom errorAtom = mock(ApplicationPayloadAtom.class);
		when(errorAtom.getEncryptor()).thenReturn(encryptor);
		when(errorAtom.getPayload()).thenReturn(payload);

		ApplicationPayloadAtom okAtom = mock(ApplicationPayloadAtom.class);
		when(okAtom.getEncryptor()).thenReturn(encryptor);
		when(okAtom.getPayload()).thenReturn(payload);

		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.just(errorAtom, okAtom));

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, ledger);
		TestObserver observer = TestObserver.create();
		api.getDecryptableData(address).subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}
}