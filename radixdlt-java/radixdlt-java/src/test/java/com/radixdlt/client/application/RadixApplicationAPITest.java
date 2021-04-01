/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomCompleteAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.utils.Pair;
import io.reactivex.subjects.PublishSubject;
import java.math.BigDecimal;

import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI.RadixApplicationAPIBuilder;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.FeeProcessor;
import com.radixdlt.client.application.translate.data.AtomToPlaintextMessageMapper;
import com.radixdlt.client.application.translate.data.PlaintextMessage;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

public class RadixApplicationAPITest {
	private RadixApplicationAPI createMockedAPI(RadixNetworkController controller, AtomStore atomStore) {
		RadixUniverse universe = mock(RadixUniverse.class);
		when(universe.getAtomStore()).thenReturn(atomStore);
		when(universe.getNetworkController()).thenReturn(controller);
		RadixIdentity identity = mock(RadixIdentity.class);

		when(identity.addSignature(any())).thenReturn(Single.just(mock(Atom.class)));

		FeeProcessor feeMapper = (actionProcessor, address, feeAtom, fee) -> Pair.of(ImmutableMap.of(), ImmutableList.of());

		return RadixApplicationAPI.defaultBuilder()
			.identity(identity)
			.universe(universe)
			.feeProcessor(feeMapper)
			.build();
	}

	private RadixApplicationAPI createMockedAPIWhichAlwaysSucceeds() {
		AtomStore atomStore = mock(AtomStore.class);
		RadixNetworkController controller = mock(RadixNetworkController.class);
		PublishSubject<RadixNodeAction> nodeActions = PublishSubject.create();
		when(controller.getActions()).thenReturn(nodeActions);
		doAnswer(a -> {
			SubmitAtomRequestAction request = a.getArgument(0);
			SubmitAtomSendAction submitting = mock(SubmitAtomSendAction.class);
			when(submitting.getUuid()).thenReturn(request.getUuid());
			SubmitAtomReceivedAction received = mock(SubmitAtomReceivedAction.class);
			when(received.getUuid()).thenReturn(request.getUuid());
			SubmitAtomStatusAction stored = mock(SubmitAtomStatusAction.class);
			when(stored.getStatusNotification()).thenReturn(new AtomStatusEvent(AtomStatus.STORED));
			when(stored.getUuid()).thenReturn(request.getUuid());
			SubmitAtomCompleteAction complete = mock(SubmitAtomCompleteAction.class);
			when(complete.getUuid()).thenReturn(request.getUuid());
			nodeActions.onNext(submitting);
			nodeActions.onNext(received);
			nodeActions.onNext(stored);
			nodeActions.onNext(complete);
			return null;
		}).when(controller).dispatch(any(SubmitAtomRequestAction.class));
		return createMockedAPI(controller, atomStore);
	}

	private void validateSuccessfulStoreAtomResult(Result result) {
		TestObserver<?> completionObserver = TestObserver.create();
		TestObserver<SubmitAtomAction> updatesObserver = TestObserver.create();
		result.toCompletable().subscribe(completionObserver);
		completionObserver.assertNoErrors();
		completionObserver.assertComplete();

		result.toObservable().subscribe(updatesObserver);
		updatesObserver.awaitTerminalEvent();
		updatesObserver.assertNoErrors();
		updatesObserver.assertComplete();
		updatesObserver.assertValueCount(3);
		updatesObserver.assertValueAt(0, atomUpdate ->
			atomUpdate instanceof SubmitAtomSendAction);
		updatesObserver.assertValueAt(1, atomUpdate ->
			atomUpdate instanceof SubmitAtomReceivedAction);
		updatesObserver.assertValueAt(2, atomUpdate ->
			((SubmitAtomStatusAction) atomUpdate).getStatusNotification().getAtomStatus().equals(AtomStatus.STORED));
	}

	@Test
	public void testNull() {
		assertThatThrownBy(() -> RadixApplicationAPI.create(null, null))
				.isInstanceOf(NullPointerException.class);

		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		assertThatThrownBy(() -> api.observeMessages(null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> api.observeTokenTransfers(null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> api.observeBalance(null, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void testZeroAtomsWithData() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		var atom = mock(Atom.class);
		AtomObservation atomObservation = AtomObservation.stored(atom, 0L);

		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.getAtomObservations(any())).thenReturn(Observable.just(atomObservation, atomObservation, atomObservation));
		when(universe.getAtomStore()).thenReturn(atomStore);

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeProcessor(mock(FeeProcessor.class))
			.addAtomMapper(new AtomToPlaintextMessageMapper())
			.build();
		TestObserver<PlaintextMessage> observer = TestObserver.create();
		api.observeMessages(address).subscribe(observer);
		observer.assertValueCount(0);
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void testZeroTransactionWallet() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixNetworkController controller = mock(RadixNetworkController.class);
		when(universe.getNetworkController()).thenReturn(controller);
		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.onSync(any())).thenReturn(Observable.just(System.currentTimeMillis()));


		when(universe.getAtomStore()).thenReturn(atomStore);
		when(universe.getAtomPuller()).thenReturn(address -> Observable.never());

		RadixAddress address = mock(RadixAddress.class);
		when(controller.getActions()).thenReturn(Observable.just(
			FetchAtomsObservationAction.of("uuid", address, mock(RadixNode.class), AtomObservation.head())
		));
		RadixIdentity identity = mock(RadixIdentity.class);

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeProcessor(mock(FeeProcessor.class))
			.addReducer(new TokenBalanceReducer())
			.build();
		TestObserver<BigDecimal> observer = TestObserver.create();
		RRI token = mock(RRI.class);

		api.observeBalance(address, token).subscribe(observer);
		observer.awaitCount(1);
		observer.assertValue(amount -> amount.compareTo(BigDecimal.ZERO) == 0);
	}
}
