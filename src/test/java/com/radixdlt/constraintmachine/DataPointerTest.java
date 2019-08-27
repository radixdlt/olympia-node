package com.radixdlt.constraintmachine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class DataPointerTest {
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