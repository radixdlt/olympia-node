package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.ledger.ParticleStore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

public class ApplicationStoreTest {

	@Test
	public void testCache() {
		RadixAddress address = mock(RadixAddress.class);
		ParticleStore store = mock(ParticleStore.class);
		MintedTokensParticle minted = mock(MintedTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(minted.getAmount()).thenReturn(UInt256.TEN);
		when(minted.getHash()).thenReturn(hash);
		TokenTypeReference token = mock(TokenTypeReference.class);
		when(minted.getTokenTypeReference()).thenReturn(token);

		when(store.getParticles(address)).thenReturn(
				Observable.<SpunParticle>just(SpunParticle.up(minted)).concatWith(Observable.never())
		);

		ApplicationState o = mock(ApplicationState.class);

		ParticleReducer<ApplicationState> reducer = mock(ParticleReducer.class);
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