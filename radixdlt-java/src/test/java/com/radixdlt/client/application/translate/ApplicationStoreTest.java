package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

public class ApplicationStoreTest {

	@Test
	public void testCache() {
		RadixAddress address = mock(RadixAddress.class);
		ParticleStore store = mock(ParticleStore.class);
		Consumable consumable = mock(Consumable.class);
		RadixHash hash = mock(RadixHash.class);
		when(consumable.getSignedAmount()).thenReturn(10L);
		when(consumable.getAmount()).thenReturn(10L);
		when(consumable.getHash()).thenReturn(hash);
		when(consumable.getSpin()).thenReturn(Spin.UP);
		when(consumable.getDson()).thenReturn(new byte[] {1});
		TokenRef token = mock(TokenRef.class);
		when(consumable.getTokenRef()).thenReturn(token);

		when(store.getParticles(address)).thenReturn(
			Observable.<Particle>just(consumable).concatWith(Observable.never())
		);

		Object o = mock(Object.class);

		ParticleReducer<Object> reducer = mock(ParticleReducer.class);
		when(reducer.initialState()).thenReturn(o);
		when(reducer.reduce(any(), any())).thenReturn(o);
		ApplicationStore<?> applicationStore = new ApplicationStore<>(store, reducer);

		TestObserver<Object> testObserver = TestObserver.create();
		applicationStore.getState(address).subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(o);
		testObserver.dispose();

		TestObserver<Object> testObserver2 = TestObserver.create();
		applicationStore.getState(address).subscribe(testObserver2);
		testObserver2.assertValue(o);

		verify(store, times(1)).getParticles(address);
	}

}