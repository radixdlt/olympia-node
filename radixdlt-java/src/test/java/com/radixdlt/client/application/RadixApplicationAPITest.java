package com.radixdlt.client.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.InsufficientFundsException;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import com.radixdlt.client.application.identity.RadixIdentity;
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
	private RadixApplicationAPI createMockedAPI(RadixLedger ledger) {
		RadixUniverse universe = mock(RadixUniverse.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixIdentity identity = mock(RadixIdentity.class);

		AtomBuilder atomBuilder = mock(AtomBuilder.class);
		when(atomBuilder.type(any())).thenReturn(atomBuilder);
		when(atomBuilder.protectors(any())).thenReturn(atomBuilder);
		when(atomBuilder.payload(any(byte[].class))).thenReturn(atomBuilder);
		Atom atom = mock(Atom.class);
		when(identity.sign(any())).thenReturn(Single.just(atom));

		Supplier<AtomBuilder> atomBuilderSupplier = () -> atomBuilder;
		UnsignedAtom unsignedAtom = mock(UnsignedAtom.class);
		when(atomBuilder.buildWithPOWFee(anyInt(), any())).thenReturn(unsignedAtom);

		return RadixApplicationAPI.create(identity, universe, atomBuilderSupplier);
	}

	private RadixLedger createMockedLedgerWhichAlwaysSucceeds() {
		RadixLedger ledger = mock(RadixLedger.class);

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

		return ledger;
	}

	private RadixApplicationAPI createMockedAPIWhichAlwaysSucceeds() {
		return createMockedAPI(createMockedLedgerWhichAlwaysSucceeds());
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
	public void testNull() {
		assertThatThrownBy(() -> RadixApplicationAPI.create(null))
			.isInstanceOf(NullPointerException.class);

		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		assertThatThrownBy(() -> api.getReadableData(null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> api.getTokenTransfers(null, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> api.getSubUnitBalance(null, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void testStoreData() {
		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		RadixAddress address = mock(RadixAddress.class);

		Data data = mock(Data.class);
		Result result = api.storeData(data, address);
		validateSuccessfulStoreDataResult(result);
	}

	@Test
	public void testStoreData2() {
		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		RadixAddress address = mock(RadixAddress.class);

		Data data = mock(Data.class);
		Result result = api.storeData(data, address, address);
		validateSuccessfulStoreDataResult(result);
	}

	@Test
	public void testStoreWithoutSubscription() {
		RadixLedger ledger = createMockedLedgerWhichAlwaysSucceeds();
		RadixApplicationAPI api = createMockedAPI(ledger);
		RadixAddress address = mock(RadixAddress.class);

		Data data = mock(Data.class);
		api.storeData(data, address, address);
		verify(ledger, times(1)).submitAtom(any());
	}

	@Test
	public void testStoreWithMultipleSubscribes() {
		RadixLedger ledger = createMockedLedgerWhichAlwaysSucceeds();
		RadixApplicationAPI api = createMockedAPI(ledger);
		RadixAddress address = mock(RadixAddress.class);

		Data data = mock(Data.class);
		Result result = api.storeData(data, address, address);
		Observable observable = result.toObservable();
		observable.subscribe();
		observable.subscribe();
		observable.subscribe();
		verify(ledger, times(1)).submitAtom(any());
	}


	@Test
	public void testUndecryptableData() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixLedger ledger = mock(RadixLedger.class);
		RadixUniverse universe = mock(RadixUniverse.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixAddress address = mock(RadixAddress.class);
		UnencryptedData unencryptedData = mock(UnencryptedData.class);

		when(identity.decrypt(any()))
			.thenReturn(Single.error(new CryptoException("Can't decrypt")))
			.thenReturn(Single.just(unencryptedData));

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

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, AtomBuilder::new);
		TestObserver observer = TestObserver.create();
		api.getReadableData(address).subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}


	@Test
	public void testZeroTransactionWallet() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		RadixAddress address = mock(RadixAddress.class);
		RadixIdentity identity = mock(RadixIdentity.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, AtomBuilder::new);

		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.empty());

		TestObserver<Long> observer = TestObserver.create();

		api.getSubUnitBalance(address, Asset.XRD).subscribe(observer);
		observer.assertValue(0L);
	}

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		RadixAddress address = mock(RadixAddress.class);
		RadixIdentity identity = mock(RadixIdentity.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, AtomBuilder::new);

		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.empty());

		TestObserver observer = TestObserver.create();
		api.transferTokens(address, address, Asset.XRD, 10).toCompletable().subscribe(observer);
		observer.assertError(new InsufficientFundsException(Asset.XRD, 0, 10));
	}
}