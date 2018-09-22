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
import com.radixdlt.client.application.translate.DataStoreTranslator;
import com.radixdlt.client.application.translate.InsufficientFundsException;
import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.function.Supplier;
import org.junit.Test;

public class RadixApplicationAPITest {
	private RadixApplicationAPI createMockedAPI(
		AtomSubmitter atomSubmitter,
		AtomStore atomStore
	) {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomSubmitter()).thenReturn(atomSubmitter);
		when(ledger.getAtomStore()).thenReturn(atomStore);
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

		return RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), atomBuilderSupplier);
	}

	private AtomSubmitter createMockedSubmissionWhichAlwaysSucceeds() {
		AtomSubmitter submission = mock(AtomSubmitter.class);

		AtomSubmissionUpdate submitting = mock(AtomSubmissionUpdate.class);
		AtomSubmissionUpdate submitted = mock(AtomSubmissionUpdate.class);
		AtomSubmissionUpdate stored = mock(AtomSubmissionUpdate.class);
		when(submitting.isComplete()).thenCallRealMethod();
		when(submitted.isComplete()).thenCallRealMethod();
		when(stored.isComplete()).thenCallRealMethod();

		when(submitting.getState()).thenReturn(AtomSubmissionState.SUBMITTING);
		when(submitted.getState()).thenReturn(AtomSubmissionState.SUBMITTED);
		when(stored.getState()).thenReturn(AtomSubmissionState.STORED);

		when(submission.submitAtom(any())).thenReturn(Observable.just(submitting, submitted, stored));

		return submission;
	}

	private RadixApplicationAPI createMockedAPIWhichAlwaysSucceeds() {
		return createMockedAPI(createMockedSubmissionWhichAlwaysSucceeds(), euid -> Observable.never());
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
		assertThatThrownBy(() -> api.getBalance(null, null))
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
		AtomSubmitter submitter = createMockedSubmissionWhichAlwaysSucceeds();
		RadixApplicationAPI api = createMockedAPI(submitter, euid -> Observable.never());

		createMockedAPIWhichAlwaysSucceeds();
		RadixAddress address = mock(RadixAddress.class);

		Data data = mock(Data.class);
		api.storeData(data, address, address);
		verify(submitter, times(1)).submitAtom(any());
	}

	@Test
	public void testStoreWithMultipleSubscribes() {
		AtomSubmitter submitter = createMockedSubmissionWhichAlwaysSucceeds();
		RadixApplicationAPI api = createMockedAPI(submitter, euid -> Observable.never());
		RadixAddress address = mock(RadixAddress.class);

		Data data = mock(Data.class);
		Result result = api.storeData(data, address, address);
		Observable observable = result.toObservable();
		observable.subscribe();
		observable.subscribe();
		observable.subscribe();
		verify(submitter, times(1)).submitAtom(any());
	}


	@Test
	public void testUndecryptableData() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);
		UnencryptedData unencryptedData = mock(UnencryptedData.class);

		when(identity.decrypt(any()))
			.thenReturn(Single.error(new CryptoException("Can't decrypt")))
			.thenReturn(Single.just(unencryptedData));

		Data data = mock(Data.class);
		DataStoreTranslator dataStoreTranslator = mock(DataStoreTranslator.class);
		when(dataStoreTranslator.fromAtom(any())).thenReturn(data, data);

		ApplicationPayloadAtom errorAtom = mock(ApplicationPayloadAtom.class);
		when(errorAtom.isMessageAtom()).thenReturn(true);
		when(errorAtom.getAsMessageAtom()).thenReturn(errorAtom);
		ApplicationPayloadAtom okAtom = mock(ApplicationPayloadAtom.class);
		when(okAtom.isMessageAtom()).thenReturn(true);
		when(okAtom.getAsMessageAtom()).thenReturn(okAtom);

		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomStore()).thenReturn(euid -> Observable.just(errorAtom, okAtom));
		when(universe.getLedger()).thenReturn(ledger);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, dataStoreTranslator, AtomBuilder::new);
		TestObserver observer = TestObserver.create();
		api.getReadableData(address).subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}


	@Test
	public void testZeroTransactionWallet() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		when(universe.getLedger()).thenReturn(ledger);
		when(ledger.getAtomStore()).thenReturn(euid -> Observable.empty());
		when(ledger.getParticleStore()).thenReturn(euid -> Observable.just(Collections.emptySet()));

		RadixAddress address = mock(RadixAddress.class);
		RadixIdentity identity = mock(RadixIdentity.class);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), AtomBuilder::new);
		TestObserver<Amount> observer = TestObserver.create();

		api.getBalance(address, Asset.TEST).subscribe(observer);
		observer.assertValue(amount -> amount.getAmountInSubunits() == 0);
	}

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		when(universe.getLedger()).thenReturn(ledger);
		when(ledger.getAtomStore()).thenReturn(euid -> Observable.empty());
		when(ledger.getParticleStore()).thenReturn(euid -> Observable.just(Collections.emptySet()));
		when(ledger.getAtomSubmitter()).thenReturn(atom -> Observable.empty());

		RadixAddress address = mock(RadixAddress.class);
		RadixIdentity identity = mock(RadixIdentity.class);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), AtomBuilder::new);

		TestObserver observer = TestObserver.create();
		api.transferTokens(address, address, Amount.subUnitsOf(10, Asset.TEST)).toCompletable().subscribe(observer);
		observer.assertError(new InsufficientFundsException(Asset.TEST, 0, 10));
	}
}