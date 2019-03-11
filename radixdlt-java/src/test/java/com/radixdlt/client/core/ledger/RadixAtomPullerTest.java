package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class RadixAtomPullerTest {
	@Test
	 public void when_many_subscribers_pull_atoms_on_the_same_address__fetch_atoms_action_should_be_emitted_once() throws Exception {
		RadixNetworkController controller = mock(RadixNetworkController.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(controller, (a, b) -> { });

		List<TestObserver> observers = Stream.iterate(TestObserver.create(), t -> TestObserver.create()).limit(10)
			.collect(Collectors.toList());

		Cancellable c = mock(Cancellable.class);
		when(controller.addReducer(any())).thenReturn(c);

		List<Disposable> disposables = observers.stream()
			.map(observer -> radixAtomPuller.pull(address).subscribe())
			.collect(Collectors.toList());

		verify(controller, times(1)).dispatch(argThat(t -> {
			if (!(t instanceof FetchAtomsRequestAction)) {
				return false;
			}

			FetchAtomsRequestAction fetchAtomsAction = (FetchAtomsRequestAction) t;
			return fetchAtomsAction.getAddress().equals(address);
		}));

		disposables.forEach(Disposable::dispose);

		verify(c, times(1)).cancel();
	}

	@Test
	public void when_empty_address_pulled__fetch_atoms_action_should_be_emitted_and_head_stored() {
		RadixNetworkController controller = mock(RadixNetworkController.class);
		BiConsumer<RadixAddress, AtomObservation> atomStore = mock(BiConsumer.class);
		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(controller, atomStore);
		RadixAddress address = mock(RadixAddress.class);
		RadixNode node = mock(RadixNode.class);

		final AtomicReference<Consumer<RadixNodeAction>> reducer = new AtomicReference<>();

		doAnswer(new Answer() {
			@Override
			public Cancellable answer(InvocationOnMock invocation) {
				Consumer<RadixNodeAction> c = invocation.getArgument(0);
				reducer.set(c);
				return () -> {
				};
			}
		}).when(controller).addReducer(any(Consumer.class));

		doAnswer(i -> {
			FetchAtomsRequestAction a = i.getArgument(0);
			FetchAtomsObservationAction action = FetchAtomsObservationAction.of(a.getUuid(), address, node, AtomObservation.head());
			reducer.get().accept(action);
			return null;
		}).when(controller).dispatch(any(FetchAtomsRequestAction.class));

		Disposable d = radixAtomPuller.pull(address).subscribe();

		verify(atomStore, times(1)).accept(eq(address), argThat(AtomObservation::isHead));

		d.dispose();
	}

	@Test
	public void when_address_pulled_and_stored_action_emitted__a_soft_store_observation_should_be_stored() {
		RadixNetworkController controller = mock(RadixNetworkController.class);
		BiConsumer<RadixAddress, AtomObservation> atomStore = mock(BiConsumer.class);
		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(controller, atomStore);
		RadixAddress address = mock(RadixAddress.class);
		RadixNode node = mock(RadixNode.class);

		final AtomicReference<Consumer<RadixNodeAction>> reducer = new AtomicReference<>();

		doAnswer(new Answer() {
			@Override
			public Cancellable answer(InvocationOnMock invocation) {
				Consumer<RadixNodeAction> c = invocation.getArgument(0);
				reducer.set(c);
				return () -> {
				};
			}
		}).when(controller).addReducer(any(Consumer.class));

		final Atom atom = mock(Atom.class);
		when(atom.addresses()).thenReturn(Stream.of(address), Stream.of(address));
		Particle p = mock(Particle.class);
		when(p.getHash()).thenReturn(mock(RadixHash.class));
		when(atom.spunParticles()).thenReturn(Stream.of(SpunParticle.up(p)), Stream.of(SpunParticle.up(p)));

		doAnswer(i -> {
			RadixNodeAction action = SubmitAtomResultAction.fromUpdate(
				"different-id",
				atom,
				node,
				new NodeAtomSubmissionUpdate(NodeAtomSubmissionState.STORED, null)
			);

			reducer.get().accept(action);
			return null;
		}).when(controller).dispatch(any(FetchAtomsRequestAction.class));

		Disposable d = radixAtomPuller.pull(address).subscribe();

		InOrder inOrder = Mockito.inOrder(atomStore, atomStore);
		inOrder.verify(atomStore).accept(eq(address), argThat(o ->
			o.isStore()
			&& o.getAtom().equals(atom)
			&& o.getUpdateType().isSoft()
		));
		inOrder.verify(atomStore).accept(eq(address), argThat(AtomObservation::isHead));

		d.dispose();
	}
}