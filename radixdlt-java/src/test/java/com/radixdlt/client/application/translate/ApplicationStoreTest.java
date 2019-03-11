package com.radixdlt.client.application.translate;

import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.ParticleStore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.ledger.TransitionedParticle;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

public class ApplicationStoreTest {

	@Test
	public void when_transitioned_particle_is_immediately_seen__initial_state_should_be_debounced() {
		RadixAddress address = mock(RadixAddress.class);
		ParticleStore store = mock(ParticleStore.class);

		when(store.getParticles(address)).thenReturn(
			Observable.just(mock(TransitionedParticle.class)).concatWith(Observable.never())
		);

		ApplicationState initialState = mock(ApplicationState.class);
		ApplicationState nextState = mock(ApplicationState.class);

		ParticleReducer<ApplicationState> reducer = mock(ParticleReducer.class);
		when(reducer.initialState()).thenReturn(initialState);
		when(reducer.reduce(any(), any())).thenReturn(nextState);
		ApplicationStore<?> applicationStore = new ApplicationStore<>(store, reducer);

		TestObserver<Object> testObserver = TestObserver.create();
		applicationStore.getState(address).subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(nextState);
		testObserver.dispose();

		verify(store, times(1)).getParticles(address);
	}

}