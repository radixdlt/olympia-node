package com.radixdlt.store;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.atoms.Particle;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;

public class StateStoresTest {
	private StateStore createStore(boolean supports, Function<Particle, Optional<Spin>> getSpinFunc) {
		return new StateStore() {
			@Override
			public boolean supports(Set<EUID> destinations) {
				return supports;
			}

			@Override
			public Optional<Spin> getSpin(Particle particle) {
				return getSpinFunc.apply(particle);
			}
		};
	}

	@Test
	public void when_an_empty_store_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_up() {
		StateStore stateStore = StateStores.virtualizeDefault(StateStores.empty(), p -> true, Spin.UP);
		assertThat(stateStore.getSpin(mock(Particle.class)))
			.contains(Spin.UP);
	}

	@Test
	public void when_an_up_store_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_up() {
		StateStore stateStore = StateStores.virtualizeDefault(createStore(true, p -> Optional.of(Spin.UP)), p -> true, Spin.UP);
		assertThat(stateStore.getSpin(mock(Particle.class)))
			.contains(Spin.UP);
	}

	@Test
	public void when_a_down_store_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_down() {
		StateStore stateStore = StateStores.virtualizeDefault(createStore(true, p -> Optional.of(Spin.DOWN)), p -> true, Spin.UP);
		assertThat(stateStore.getSpin(mock(Particle.class)))
			.contains(Spin.DOWN);
	}

	@Test
	public void when_an_unknown_store_supporting_all_shards_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_up() {
		StateStore stateStore = StateStores.virtualizeDefault(createStore(true, p -> Optional.empty()), p -> true, Spin.UP);
		assertThat(stateStore.getSpin(mock(Particle.class)))
			.contains(Spin.UP);
	}

	@Test
	public void when_an_unknown_store_supporting_no_shards_is_virtualized_default_up_and_spin_is_requested__then_it_should_return_up() {
		StateStore stateStore = StateStores.virtualizeDefault(createStore(false, p -> Optional.empty()), p -> true, Spin.UP);
		assertThat(stateStore.getSpin(mock(Particle.class))).isNotPresent();
	}


	@Test
	public void when_a_store_is_virtualized_overwrite_up_and_spin_is_requested__then_it_should_return_up() {
		StateStore stateStore = StateStores.virtualizeOverwrite(mock(StateStore.class), p -> true, Spin.UP);
		assertThat(stateStore.getSpin(mock(Particle.class)))
			.contains(Spin.UP);
	}
}