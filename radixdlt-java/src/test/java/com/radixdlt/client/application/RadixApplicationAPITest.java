package com.radixdlt.client.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.DataStoreTranslator;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.AtomPuller;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.ledger.ParticleStore;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
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

		Atom atom = mock(Atom.class);
		when(identity.sign(any())).thenReturn(Single.just(atom));

		UnsignedAtom unsignedAtom = mock(UnsignedAtom.class);
		FeeMapper feeMapper = (a, b, c) -> Collections.emptyList();

		return RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), feeMapper);
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
		assertThatThrownBy(() -> api.getTokenTransfers(null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> api.getBalance(null, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void testStoreData() {
		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		ECPublicKey key = mock(ECPublicKey.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(key);

		Data data = mock(Data.class);
		when(data.getBytes()).thenReturn(new byte[0]);
		Result result = api.storeData(data, address);
		validateSuccessfulStoreDataResult(result);
	}

	@Test
	public void testStoreData2() {
		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		ECPublicKey key = mock(ECPublicKey.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(key);

		Data data = mock(Data.class);
		when(data.getBytes()).thenReturn(new byte[0]);
		Result result = api.storeData(data, address, address);
		validateSuccessfulStoreDataResult(result);
	}

	@Test
	public void testStoreWithoutSubscription() {
		AtomSubmitter submitter = createMockedSubmissionWhichAlwaysSucceeds();
		RadixApplicationAPI api = createMockedAPI(submitter, euid -> Observable.never());

		createMockedAPIWhichAlwaysSucceeds();
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));

		Data data = mock(Data.class);
		when(data.getBytes()).thenReturn(new byte[0]);
		api.storeData(data, address, address);
		verify(submitter, times(1)).submitAtom(any());
	}

	@Test
	public void testStoreWithMultipleSubscribes() {
		AtomSubmitter submitter = createMockedSubmissionWhichAlwaysSucceeds();
		RadixApplicationAPI api = createMockedAPI(submitter, euid -> Observable.never());
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));

		Data data = mock(Data.class);
		when(data.getBytes()).thenReturn(new byte[0]);
		Result result = api.storeData(data, address, address);
		Observable observable = result.toObservable();
		observable.subscribe();
		observable.subscribe();
		observable.subscribe();
		verify(submitter, times(1)).submitAtom(any());
	}

	@Test
	public void testZeroAtomsWithData() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		Atom atom = mock(Atom.class);
		when(atom.getDataParticles()).thenReturn(Collections.emptyList());
		AtomObservation atomObservation = AtomObservation.storeAtom(atom);

		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomStore()).thenReturn(euid -> Observable.just(atomObservation, atomObservation, atomObservation));
		when(universe.getLedger()).thenReturn(ledger);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), mock(PowFeeMapper.class));
		TestObserver<UnencryptedData> observer = TestObserver.create();
		api.getReadableData(address).subscribe(observer);
		observer.assertValueCount(0);
		observer.assertComplete();
		observer.assertNoErrors();
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
		when(data.getBytes()).thenReturn(new byte[0]);
		DataStoreTranslator dataStoreTranslator = mock(DataStoreTranslator.class);
		when(dataStoreTranslator.fromAtom(any(Atom.class))).thenReturn(Optional.of(data), Optional.of(data));

		Atom errorAtom = mock(Atom.class);
		Atom okAtom = mock(Atom.class);

		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomStore()).thenReturn(euid -> Observable.just(errorAtom, okAtom).map(AtomObservation::storeAtom));
		when(universe.getLedger()).thenReturn(ledger);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, dataStoreTranslator, mock(PowFeeMapper.class));
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
		when(ledger.getParticleStore()).thenReturn(euid -> Observable.never());

		RadixAddress address = mock(RadixAddress.class);
		RadixIdentity identity = mock(RadixIdentity.class);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), mock(PowFeeMapper.class));
		TestObserver<BigDecimal> observer = TestObserver.create();
		TokenClassReference token = mock(TokenClassReference.class);

		api.getBalance(address, token).subscribe(observer);
		observer.awaitCount(1);
		observer.assertValue(amount -> amount.compareTo(BigDecimal.ZERO) == 0);
	}


	@Test
	public void testPullOnReadDataOfOtherAddresses() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		AtomPuller puller = mock(AtomPuller.class);
		when(ledger.getAtomPuller()).thenReturn(puller);
		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.getAtoms(any())).thenReturn(Observable.never());
		when(ledger.getAtomStore()).thenReturn(atomStore);
		when(universe.getLedger()).thenReturn(ledger);

		RadixIdentity identity = mock(RadixIdentity.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), mock(PowFeeMapper.class));
		TestObserver<Data> testObserver = TestObserver.create();
		api.getData(address).subscribe(testObserver);
		verify(puller, times(1)).pull(address);
	}

	@Test
	public void testPullOnGetBalanceOfOtherAddresses() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		AtomPuller puller = mock(AtomPuller.class);
		when(ledger.getAtomPuller()).thenReturn(puller);
		ParticleStore particleStore = mock(ParticleStore.class);
		when(particleStore.getParticles(any())).thenReturn(Observable.never());
		when(ledger.getParticleStore()).thenReturn(particleStore);
		when(universe.getLedger()).thenReturn(ledger);

		RadixIdentity identity = mock(RadixIdentity.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixApplicationAPI api = RadixApplicationAPI.create(identity, universe, DataStoreTranslator.getInstance(), mock(PowFeeMapper.class));
		TestObserver<BigDecimal> testObserver = TestObserver.create();
		TokenClassReference token = mock(TokenClassReference.class);
		api.getBalance(address, token).subscribe(testObserver);
		verify(puller, times(1)).pull(address);
	}
}