package com.radixdlt.client.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.objects.EncryptedData;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.function.Supplier;
import org.junit.Test;

public class RadixApplicationAPITest {
	private RadixApplicationAPI createMockedAPIWhichAlwaysSucceeds() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixLedger ledger = mock(RadixLedger.class);
		AtomBuilder atomBuilder = mock(AtomBuilder.class);
		when(atomBuilder.type(any())).thenReturn(atomBuilder);
		when(atomBuilder.protectors(any())).thenReturn(atomBuilder);
		when(atomBuilder.payload(any(byte[].class))).thenReturn(atomBuilder);
		Atom atom = mock(Atom.class);
		when(identity.sign(any())).thenReturn(Single.just(atom));

		AtomSubmissionUpdate submitting = mock(AtomSubmissionUpdate.class);
		AtomSubmissionUpdate submitted = mock(AtomSubmissionUpdate.class);
		AtomSubmissionUpdate stored = mock(AtomSubmissionUpdate.class);
		when(submitting.isComplete()).thenCallRealMethod();
		when(submitted.isComplete()).thenCallRealMethod();
		when(stored.isComplete()).thenCallRealMethod();

		when(submitting.getState()).thenReturn(AtomSubmissionState.SUBMITTING);
		when(submitted.getState()).thenReturn(AtomSubmissionState.SUBMITTED);
		when(stored.getState()).thenReturn(AtomSubmissionState.STORED);

		when(ledger.submitAtom(any())).thenReturn(Observable.just(submitting, submitted, stored));
		Supplier<AtomBuilder> atomBuilderSupplier = () -> atomBuilder;
		UnsignedAtom unsignedAtom = mock(UnsignedAtom.class);
		when(atomBuilder.buildWithPOWFee(anyInt(), any())).thenReturn(unsignedAtom);

		return RadixApplicationAPI.create(identity, ledger, atomBuilderSupplier);
	}

	private void validateSuccessfulStoreDataResult(Result result) {
		TestObserver completionObserver = TestObserver.create();
		TestObserver<AtomSubmissionUpdate> updatesObserver = TestObserver.create();
		result.toCompletable().subscribe(completionObserver);
		completionObserver.assertNoErrors();
		completionObserver.assertComplete();

		result.toObservable().subscribe(updatesObserver);
		updatesObserver.assertNoErrors();
		updatesObserver.assertComplete();
		updatesObserver.assertValueCount(3);
		updatesObserver.assertValueAt(0, atomUpdate -> atomUpdate.getState().equals(AtomSubmissionState.SUBMITTING));
		updatesObserver.assertValueAt(1, atomUpdate -> atomUpdate.getState().equals(AtomSubmissionState.SUBMITTED));
		updatesObserver.assertValueAt(2, atomUpdate -> atomUpdate.getState().equals(AtomSubmissionState.STORED));
	}

	@Test
	public void testStoreData() {
		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		RadixAddress address = mock(RadixAddress.class);

		EncryptedData encryptedData = mock(EncryptedData.class);
		Result result = api.storeData(encryptedData, address);
		validateSuccessfulStoreDataResult(result);
	}

	@Test
	public void testStoreData2() {
		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		RadixAddress address = mock(RadixAddress.class);

		EncryptedData encryptedData = mock(EncryptedData.class);
		Result result = api.storeData(encryptedData, address, address);
		validateSuccessfulStoreDataResult(result);
	}

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

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, ledger, AtomBuilder::new);
		TestObserver observer = TestObserver.create();
		api.getDecryptableData(address).subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}
}