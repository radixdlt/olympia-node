package com.radixdlt.client.application;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomCompleteAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.ReplaySubject;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.radix.common.ID.EUID;
import org.radix.common.tuples.Pair;

public class RadixApplicationAPITest {
	private RadixApplicationAPI createMockedAPI(RadixNetworkController controller, AtomStore atomStore) {
		RadixUniverse universe = mock(RadixUniverse.class);
		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomStore()).thenReturn(atomStore);
		when(universe.getLedger()).thenReturn(ledger);
		when(universe.getNetworkController()).thenReturn(controller);
		RadixIdentity identity = mock(RadixIdentity.class);

		Atom atom = mock(Atom.class);
		when(atom.getRequiredFirstShard()).thenReturn(Collections.singleton(0L));
		when(identity.sign(any())).thenReturn(Single.just(atom));

		FeeMapper feeMapper = (a, b, c) -> Pair.of(ImmutableMap.of(), ImmutableList.of());

		return new RadixApplicationAPIBuilder()
			.identity(identity)
			.universe(universe)
			.feeMapper(feeMapper)
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
			when(stored.getStatusNotification()).thenReturn(new AtomStatusNotification(AtomStatus.STORED));
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

	private void validateSuccessfulStoreDataResult(Result result) {
		TestObserver completionObserver = TestObserver.create();
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
		AtomStore atomStore = mock(AtomStore.class);
		RadixNetworkController controller = mock(RadixNetworkController.class);
		when(controller.getActions()).thenReturn(Observable.never());
		RadixApplicationAPI api = createMockedAPI(controller, atomStore);

		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		when(api.getMyAddress()).thenReturn(address);

		api.sendMessage(new byte[0], false, address);
		verify(controller, times(1)).dispatch(any(SubmitAtomRequestAction.class));
	}

	@Test
	public void testStoreWithMultipleSubscribes() {
		RadixNetworkController controller = mock(RadixNetworkController.class);
		when(controller.getActions()).thenReturn(Observable.never());
		AtomStore atomStore = mock(AtomStore.class);
		RadixApplicationAPI api = createMockedAPI(controller, atomStore);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		when(api.getMyAddress()).thenReturn(address);

		Result result = api.sendMessage(new byte[0], false, address);
		Observable observable = result.toObservable();
		observable.subscribe();
		observable.subscribe();
		observable.subscribe();
		verify(controller, times(1)).dispatch(any(SubmitAtomRequestAction.class));
	}

	@Test
	public void testZeroAtomsWithData() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		Atom atom = mock(Atom.class);
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
		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(EUID.ONE);
		when(particle.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));
		when(particle.getHid()).thenReturn(EUID.ONE);
		Atom atom = new Atom(Collections.singletonList(ParticleGroup.of(SpunParticle.up(particle))), 0L);
		RadixIdentity identity = mock(RadixIdentity.class);
		when(identity.sign(any())).thenReturn(Single.just(atom));
		RadixUniverse universe = mock(RadixUniverse.class);

		RadixNetworkController controller = mock(RadixNetworkController.class);
		ReplaySubject<RadixNodeAction> nodeActions = ReplaySubject.create();

		JsonObject errorData = new JsonObject();

		doAnswer(a -> {
			SubmitAtomRequestAction request = a.getArgument(0);
			SubmitAtomStatusAction update = mock(SubmitAtomStatusAction.class);
			when(update.getUuid()).thenReturn(request.getUuid());
			when(update.getAtom()).thenReturn(atom);
			when(update.getStatusNotification()).thenReturn(new AtomStatusNotification(AtomStatus.EVICTED_CONFLICT_LOSER, errorData));
			SubmitAtomCompleteAction complete = mock(SubmitAtomCompleteAction.class);
			when(complete.getUuid()).thenReturn(request.getUuid());
			nodeActions.onNext(update);
			nodeActions.onNext(complete);
			return null;
		}).when(controller).dispatch(any(SubmitAtomRequestAction.class));

		when(controller.getActions()).thenReturn(nodeActions);
		when(universe.getNetworkController()).thenReturn(controller);

		Ledger ledger = mock(Ledger.class);
		when(ledger.getAtomStore()).thenReturn(mock(AtomStore.class));
		when(universe.getLedger()).thenReturn(ledger);
		Action action = mock(Action.class);

		StatelessActionToParticleGroupsMapper actionMapper = mock(StatelessActionToParticleGroupsMapper.class);
		when(actionMapper.mapToParticleGroups(eq(action))).thenReturn(Collections.singletonList(ParticleGroup.of(SpunParticle.up(particle))));
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

	@Test
	public void when_an_api_is_created_with_set_nodes_and_get_network_state_is_called__the_nodes_should_be_returned() {
		RadixIdentity identity = mock(RadixIdentity.class);
		RadixUniverseConfig universeConfig = RadixUniverseConfigs.getBetanet();
		BootstrapConfig config = new BootstrapConfig() {
			@Override
			public RadixUniverseConfig getConfig() {
				return universeConfig;
			}

			@Override
			public List<RadixNetworkEpic> getDiscoveryEpics() {
				return Collections.emptyList();
			}

			@Override
			public Set<RadixNode> getInitialNetwork() {
				return Sets.newHashSet(
					mock(RadixNode.class),
					mock(RadixNode.class)
				);
			}
		};

		TestObserver<RadixNetworkState> testObserver = TestObserver.create();
		RadixApplicationAPI api = RadixApplicationAPI.create(config, identity);
		api.getNetworkState().subscribe(testObserver);
		testObserver.assertValue(state -> state.getNodes().size() == 2);
	}
}