package com.radixdlt.store;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;

public class CMStoresTest {
	private CMStore createStore(boolean supports, Function<Particle, Spin> getSpinFunc) {
		return new CMStore() {
			@Override
			public boolean supports(Set<EUID> destinations) {
				return supports;
			}

			@Override
			public Spin getSpin(Particle particle) {
				return getSpinFunc.apply(particle);
			}
		};
	}

	@Test
	public void when_an_empty_store_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_up() {
		CMStore cmStore = CMStores.virtualizeDefault(CMStores.empty(), p -> true, Spin.UP);
		assertThat(cmStore.getSpin(mock(Particle.class))).isEqualTo(Spin.UP);
	}

	@Test
	public void when_an_up_store_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_up() {
		CMStore cmStore = CMStores.virtualizeDefault(createStore(true, p -> Spin.UP), p -> true, Spin.UP);
		assertThat(cmStore.getSpin(mock(Particle.class))).isEqualTo(Spin.UP);
	}

	@Test
	public void when_a_down_store_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_down() {
		CMStore cmStore = CMStores.virtualizeDefault(createStore(true, p -> Spin.DOWN), p -> true, Spin.UP);
		assertThat(cmStore.getSpin(mock(Particle.class))).isEqualTo(Spin.DOWN);
	}
}