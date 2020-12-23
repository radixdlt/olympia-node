/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import java.util.function.Function;
import org.junit.Test;

public class CMStoresTest {
	private CMStore createStore(boolean supports, Function<Particle, Spin> getSpinFunc) {
		return new CMStore() {

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