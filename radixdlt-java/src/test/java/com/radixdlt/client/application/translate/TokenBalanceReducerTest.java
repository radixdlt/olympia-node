package com.radixdlt.client.application.translate;

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

public class TokenBalanceReducerTest {

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
		TokenRef token = mock(TokenRef.class);
		when(consumable.getTokenRef()).thenReturn(token);

		when(store.getParticles(address)).thenReturn(
			Observable.<Particle>just(consumable).concatWith(Observable.never())
		);
		TokenBalanceReducer reducer = new TokenBalanceReducer(store);

		TestObserver<TokenBalanceState> testObserver = TestObserver.create();
		reducer.getState(address).subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(state -> state.getBalance().get(token) == 10L);
		testObserver.dispose();

		TestObserver<TokenBalanceState> testObserver2 = TestObserver.create();
		reducer.getState(address).subscribe(testObserver2);
		testObserver2.assertValue(state -> state.getBalance().get(token) == 10L);

		verify(store, times(1)).getParticles(address);
	}

}