package com.radixdlt.client.application;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.RadixApplicationAPI.RadixApplicationAPIBuilder;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.data.AtomToDecryptedMessageMapper;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.AtomPuller;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.radix.common.tuples.Pair;

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

		FeeMapper feeMapper = (a, b, c) -> Pair.of(ImmutableMap.of(), ImmutableList.of());

		return new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeMapper(feeMapper)
			.build();
	}

	private AtomSubmitter createMockedSubmissionWhichAlwaysSucceeds() {
		AtomSubmitter submission = mock(AtomSubmitter.class);

		SubmitAtomAction submitting = mock(SubmitAtomSendAction.class);

		SubmitAtomReceivedAction submitted = mock(SubmitAtomReceivedAction.class);

		SubmitAtomResultAction stored = mock(SubmitAtomResultAction.class);
		when(stored.getType()).thenReturn(SubmitAtomResultActionType.STORED);

		when(submission.submitAtom(any())).thenReturn(Observable.just(submitting, submitted, stored));

		return submission;
	}

	private RadixApplicationAPI createMockedAPIWhichAlwaysSucceeds() {
		AtomStore atomStore = mock(AtomStore.class);
		return createMockedAPI(createMockedSubmissionWhichAlwaysSucceeds(), atomStore);
	}

	private void validateSuccessfulStoreDataResult(Result result) {
		TestObserver completionObserver = TestObserver.create();
		TestObserver<SubmitAtomAction> updatesObserver = TestObserver.create();
		result.toCompletable().subscribe(completionObserver);
		completionObserver.assertNoErrors();
		completionObserver.assertComplete();

		result.toObservable().subscribe(updatesObserver);
		updatesObserver.assertNoErrors();
		updatesObserver.assertComplete();
		updatesObserver.assertValueCount(3);
		updatesObserver.assertValueAt(0, atomUpdate ->
			atomUpdate instanceof SubmitAtomSendAction);
		updatesObserver.assertValueAt(1, atomUpdate ->
			atomUpdate instanceof SubmitAtomReceivedAction);
		updatesObserver.assertValueAt(2, atomUpdate ->
			((SubmitAtomResultAction) atomUpdate).getType().equals(SubmitAtomResultActionType.STORED));
	}

	@Test
	public void testNull() {
		assertThatThrownBy(() -> RadixApplicationAPI.create(null, null))
				.isInstanceOf(NullPointerException.class);

		RadixApplicationAPI api = createMockedAPIWhichAlwaysSucceeds();
		assertThatThrownBy(() -> api.getMessages(null))
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
		when(api.getMyAddress()).thenReturn(address);

		Result result = api.sendMessage(new byte[0], false, address);
		validateSuccessfulStoreDataResult(result);
	}

	@Test
	public void testStoreWithoutSubscription() {
		AtomSubmitter submitter = createMockedSubmissionWhichAlwaysSucceeds();
		AtomStore atomStore = mock(AtomStore.class);
		RadixApplicationAPI api = createMockedAPI(submitter, atomStore);

		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		when(api.getMyAddress()).thenReturn(address);

		api.sendMessage(new byte[0], false, address);
		verify(submitter, times(1)).submitAtom(any());
	}

	@Test
	public void testStoreWithMultipleSubscribes() {
		AtomSubmitter submitter = createMockedSubmissionWhichAlwaysSucceeds();
		AtomStore atomStore = mock(AtomStore.class);
		RadixApplicationAPI api = createMockedAPI(submitter, atomStore);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		when(api.getMyAddress()).thenReturn(address);

		Result result = api.sendMessage(new byte[0], false, address);
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
		when(atom.getMessageParticles()).thenReturn(Collections.emptyList());
		AtomObservation atomObservation = AtomObservation.stored(atom);

		Ledger ledger = mock(Ledger.class);
		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.getAtomObservations(any())).thenReturn(Observable.just(atomObservation, atomObservation, atomObservation));
		when(ledger.getAtomStore()).thenReturn(atomStore);
		when(universe.getLedger()).thenReturn(ledger);

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeMapper(mock(PowFeeMapper.class))
			.addAtomMapper(new AtomToDecryptedMessageMapper())
			.build();
		TestObserver<DecryptedMessage> observer = TestObserver.create();
		api.getMessages(address).subscribe(observer);
		observer.assertValueCount(0);
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void testZeroTransactionWallet() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixNetworkController controller = mock(RadixNetworkController.class);
		when(universe.getNetworkController()).thenReturn(controller);
		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.onSync(any())).thenReturn(Observable.just(System.currentTimeMillis()));


		when(ledger.getAtomStore()).thenReturn(atomStore);
		when(ledger.getAtomPuller()).thenReturn(address -> Observable.never());

		RadixAddress address = mock(RadixAddress.class);
		when(controller.getActions()).thenReturn(Observable.just(
			FetchAtomsObservationAction.of("uuid", address, mock(RadixNode.class), AtomObservation.head())
		));
		RadixIdentity identity = mock(RadixIdentity.class);

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeMapper(mock(PowFeeMapper.class))
			.addReducer(new TokenBalanceReducer())
			.build();
		TestObserver<BigDecimal> observer = TestObserver.create();
		RRI token = mock(RRI.class);

		api.getBalance(address, token).subscribe(observer);
		observer.awaitCount(1);
		observer.assertValue(amount -> amount.compareTo(BigDecimal.ZERO) == 0);
	}

	@Test
	public void testPullOnReadDataOfOtherAddresses() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		AtomPuller puller = mock(AtomPuller.class);
		when(puller.pull(any())).thenReturn(Observable.never());
		when(ledger.getAtomPuller()).thenReturn(puller);
		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.getAtomObservations(any())).thenReturn(Observable.never());
		when(ledger.getAtomStore()).thenReturn(atomStore);
		when(universe.getLedger()).thenReturn(ledger);

		RadixIdentity identity = mock(RadixIdentity.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeMapper(mock(PowFeeMapper.class))
			.addAtomMapper(new AtomToDecryptedMessageMapper())
			.build();
		TestObserver<DecryptedMessage> testObserver = TestObserver.create();
		api.getMessages(address).subscribe(testObserver);
		verify(puller, times(1)).pull(address);
	}

	@Test
	public void testPullOnGetBalanceOfOtherAddresses() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);

		RadixNetworkController controller = mock(RadixNetworkController.class);
		when(universe.getNetworkController()).thenReturn(controller);
		when(controller.getActions()).thenReturn(Observable.never());

		AtomStore atomStore = mock(AtomStore.class);
		when(atomStore.onSync(any())).thenReturn(Observable.just(System.currentTimeMillis()));
		when(ledger.getAtomStore()).thenReturn(atomStore);

		AtomPuller puller = mock(AtomPuller.class);
		when(ledger.getAtomPuller()).thenReturn(puller);
		when(puller.pull(any())).thenReturn(Observable.never());
		when(universe.getLedger()).thenReturn(ledger);

		RadixIdentity identity = mock(RadixIdentity.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeMapper(mock(PowFeeMapper.class))
			.addReducer(new TokenBalanceReducer())
			.build();
		TestObserver<BigDecimal> testObserver = TestObserver.create();
		RRI token = mock(RRI.class);
		api.getBalance(address, token).subscribe(testObserver);
		verify(puller, times(1)).pull(address);
	}

	@Test
	public void testErrorMapper() {
		Particle particle = mock(Particle.class);
		Atom atom = new Atom(Collections.singletonList(ParticleGroup.of(SpunParticle.up(particle))), 0L);
		RadixIdentity identity = mock(RadixIdentity.class);
		when(identity.sign(any())).thenReturn(Single.just(atom));
		RadixUniverse universe = mock(RadixUniverse.class);
		JsonObject errorData = new JsonObject();

		AtomSubmitter atomSubmitter = mock(AtomSubmitter.class);
		SubmitAtomResultAction update = mock(SubmitAtomResultAction.class);
		when(update.getAtom()).thenReturn(atom);
		when(update.getType()).thenReturn(SubmitAtomResultActionType.COLLISION);
		when(update.getData()).thenReturn(errorData);

		when(atomSubmitter.submitAtom(eq(atom)))
			.thenReturn(Observable.just(update));

		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomSubmitter()).thenReturn(atomSubmitter);
		when(universe.getLedger()).thenReturn(ledger);
		Action action = mock(Action.class);

		StatelessActionToParticleGroupsMapper actionMapper = mock(StatelessActionToParticleGroupsMapper.class);
		when(actionMapper.mapToParticleGroups(eq(action))).thenReturn(Collections.singletonList(ParticleGroup.of(SpunParticle.up(particle))));
		when(actionMapper.sideEffects(any())).thenReturn(Collections.emptyList());
		AtomErrorToExceptionReasonMapper errorMapper = mock(AtomErrorToExceptionReasonMapper.class);
		ActionExecutionExceptionReason reason = mock(ActionExecutionExceptionReason.class);
		when(errorMapper.mapAtomErrorToExceptionReasons(any(), eq(errorData))).thenReturn(Stream.of(reason));

		RadixApplicationAPI api = new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.addStatelessParticlesMapper(actionMapper)
			.feeMapper((x, y, z) -> Pair.of(ImmutableMap.of(), ImmutableList.of()))
			.addAtomErrorMapper(errorMapper)
			.build();

		TestObserver testObserver = TestObserver.create();
		api.execute(action).toCompletable().subscribe(testObserver);
		testObserver.assertError(ActionExecutionException.class);
		testObserver.assertError(e -> ((ActionExecutionException) e).getReasons().contains(reason));
	}
}