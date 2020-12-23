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

package com.radixdlt.constraintmachine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class DataPointerTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(DataPointer.class)
				.withIgnoredFields("pointerToIssue") // derived from other field(s), used for caching
				.verify();
	}

	@Test
	public void when_data_pointer_of_atom_is_created__then_no_exception_is_thrown() {
		DataPointer.ofAtom();
	}

	@Test
	public void when_data_pointer_of_zero_or_positive_particle_group_index_is_created__then_no_exception_is_thrown() {
		DataPointer.ofParticleGroup(0);
		DataPointer.ofParticleGroup(1);
	}

	@Test
	public void when_data_pointer_of_zero_or_positive_particle_index_is_created__then_no_exception_is_thrown() {
		DataPointer.ofParticle(0, 0);
		DataPointer.ofParticle(0, 1);
	}

	@Test
	public void when_data_pointer_with_particle_index_and_bad_particle_group_index_is_created__then_an_illegal_state_exception_is_thrown() {
		assertThatThrownBy(() -> new DataPointer(-1, 1)).isInstanceOf(IllegalArgumentException.class);
	}
}