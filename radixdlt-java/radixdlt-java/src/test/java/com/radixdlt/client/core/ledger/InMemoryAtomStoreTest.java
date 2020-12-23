/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.ledger;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.google.common.hash.HashCode;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.Test;

import com.radixdlt.identifiers.RadixAddress;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;
import com.radixdlt.identifiers.AID;

public class InMemoryAtomStoreTest {
	private AtomObservation mockDeletedAtom(Atom atom, RadixAddress address) {
		HashCode hash = mock(HashCode.class);
		AID hid = mock(AID.class);
		when(atom.getAid()).thenReturn(hid);
		when(atom.getHash()).thenReturn(hash);
		when(atom.addresses()).thenAnswer(inv -> Stream.of(address));

		return AtomObservation.deleted(atom);
	}

	private AtomObservation mockStoredAtom(Atom atom, RadixAddress address) {
		HashCode hash = mock(HashCode.class);
		AID hid = mock(AID.class);
		when(atom.getAid()).thenReturn(hid);
		when(atom.getHash()).thenReturn(hash);
		when(atom.addresses()).thenAnswer(inv -> Stream.of(address));

		return AtomObservation.stored(atom, 0L);
	}

	private AtomObservation mockStoredAtom(Atom atom, SpunParticle spun0, SpunParticle spun1, RadixAddress address) {
		HashCode hash = mock(HashCode.class);
		AID hid = mock(AID.class);
		when(atom.spunParticles()).thenAnswer(inv -> Stream.of(spun0, spun1));
		when(atom.particles(any())).thenCallRealMethod().thenCallRealMethod().thenCallRealMethod();
		when(atom.getAid()).thenReturn(hid);
		when(atom.getHash()).thenReturn(hash);
		when(atom.addresses()).thenAnswer(inv -> Stream.of(address));

		return AtomObservation.stored(atom, 0L);
	}

	private AtomObservation mockStoredAtom(Atom atom, SpunParticle spun, RadixAddress address, boolean soft) {
		HashCode hash = mock(HashCode.class);
		AID hid = mock(AID.class);
		when(atom.spunParticles()).thenAnswer(inv -> Stream.of(spun));
		when(atom.particles(any())).thenCallRealMethod().thenCallRealMethod().thenCallRealMethod();
		when(atom.getAid()).thenReturn(hid);
		when(atom.getHash()).thenReturn(hash);
		when(atom.addresses()).thenAnswer(inv -> Stream.of(address));

		return soft ? AtomObservation.softStored(atom, 0L) : AtomObservation.stored(atom, 0L);
	}

	private AtomObservation mockStoredAtom(Atom atom, SpunParticle spun, RadixAddress address) {
		return mockStoredAtom(atom, spun, address, false);
	}

	private AtomObservation mockStoredAtom(RadixAddress address) {
		Atom atom = mock(Atom.class);
		return mockStoredAtom(atom, address);
	}

	@Test
	public void when_subscribed_before_an_atom_is_stored_on_a_different_address__then_the_atom_should_not_be_observed() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();

		RadixAddress otherAddress = mock(RadixAddress.class);
		AtomObservation atomObservation = mockStoredAtom(otherAddress);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		RadixAddress address = mock(RadixAddress.class);
		inMemoryAtomStore.getAtomObservations(address).subscribe(testObserver);
		inMemoryAtomStore.store(otherAddress, atomObservation);
		testObserver.assertEmpty();
	}

	@Test
	public void when_subscribed_before_an_atom_is_stored_on_same_address__then_the_atom_should_be_observed() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();

		RadixAddress address = mock(RadixAddress.class);
		AtomObservation atomObservation = mockStoredAtom(address);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtomObservations(address).subscribe(testObserver);
		inMemoryAtomStore.store(address, atomObservation);
		testObserver.assertValue(atomObservation);
	}

	@Test
	public void when_subscribed_after_an_atom_is_stored_on_a_different_address__then_the_atom_should_not_be_observed() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();

		RadixAddress otherAddress = mock(RadixAddress.class);
		AtomObservation atomObservation = mockStoredAtom(otherAddress);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		RadixAddress address = mock(RadixAddress.class);
		inMemoryAtomStore.store(otherAddress, atomObservation);
		inMemoryAtomStore.getAtomObservations(address).subscribe(testObserver);
		testObserver.assertEmpty();
	}

	@Test
	public void when_subscribed_after_an_atom_is_stored_on_same_address__then_the_atom_should_be_observed() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();

		RadixAddress address = mock(RadixAddress.class);
		AtomObservation atomObservation = mockStoredAtom(address);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.store(address, atomObservation);
		inMemoryAtomStore.getAtomObservations(address).subscribe(testObserver);
		testObserver.assertValue(atomObservation);
	}

	@Test
	public void when_receiving_atom_deletes_for_atoms_which_have_not_been_seen__store_should_not_propagate_delete_event() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();

		Atom atom = mock(Atom.class);
		RadixAddress address = mock(RadixAddress.class);
		AtomObservation deleteObservation = mockDeletedAtom(atom, address);
		AtomObservation storeObservation = mockStoredAtom(address);

		inMemoryAtomStore.store(address, deleteObservation);
		inMemoryAtomStore.store(address, storeObservation);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtomObservations(address).subscribe(testObserver);
		testObserver.assertValue(storeObservation);
	}

	@Test
	public void when_receiving_atom_store_then_delete_then_store_for_an_atom_then_subscribe__store_should_propagate_one_store_event() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);
		RadixAddress address = mock(RadixAddress.class);
		AtomObservation storeObservation = mockStoredAtom(atom, address);
		AtomObservation deleteObservation = mockDeletedAtom(atom, address);
		inMemoryAtomStore.store(address, storeObservation);
		inMemoryAtomStore.store(address, deleteObservation);
		inMemoryAtomStore.store(address, storeObservation);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtomObservations(address).subscribe(testObserver);
		testObserver.assertValues(storeObservation);
	}

	@Test
	public void when_getting_up_particles_with_an_empty_store__store_should_return_an_empty_stream() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(mock(RadixAddress.class), null);
		assertThat(upParticles).isEmpty();
	}


	@Test
	public void when_getting_up_particles_from_store_with_an_atom_with_no_particles__store_should_return_an_empty_stream() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		RadixAddress someAddress = mock(RadixAddress.class);
		inMemoryAtomStore.store(someAddress, mockStoredAtom(someAddress));
		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(someAddress, null);
		assertThat(upParticles).isEmpty();
	}

	@Test
	public void when_getting_up_particles_from_store_with_an_atom_with_one_up_particle__store_should_return_that_particle() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		RadixAddress someAddress = mock(RadixAddress.class);
		Particle particle = mock(Particle.class);
		when(particle.getShardables()).thenReturn(Collections.singleton(someAddress));
		inMemoryAtomStore.store(someAddress, mockStoredAtom(mock(Atom.class), SpunParticle.up(particle), someAddress));
		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(someAddress, null);
		assertThat(upParticles).containsExactly(particle);
	}

	@Test
	public void when_getting_up_particles_from_store_with_an_atom_with_one_down_particle__store_should_return_an_empty_stream() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		RadixAddress someAddress = mock(RadixAddress.class);
		Particle particle = mock(Particle.class);
		when(particle.getShardables()).thenReturn(Collections.singleton(someAddress));
		inMemoryAtomStore.store(someAddress, mockStoredAtom(mock(Atom.class), SpunParticle.down(particle), someAddress));
		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(someAddress, null);
		assertThat(upParticles).isEmpty();
	}

	@Test
	public void when_getting_up_particles_from_store_with_an_atom_with_one_up_particle_then_deleted__store_should_return_an_empty_stream() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		RadixAddress someAddress = mock(RadixAddress.class);
		Particle particle = mock(Particle.class);
		when(particle.getShardables()).thenReturn(Collections.singleton(someAddress));
		Atom atom = mock(Atom.class);
		inMemoryAtomStore.store(someAddress, mockStoredAtom(atom, SpunParticle.up(particle), someAddress));
		inMemoryAtomStore.store(someAddress, mockDeletedAtom(atom, someAddress));

		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(someAddress, null);
		assertThat(upParticles).isEmpty();
	}

	@Test
	public void when_getting_up_particles_from_store_with_dependent_deletes__store_should_return_an_empty_stream() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		RadixAddress someAddress = mock(RadixAddress.class);
		Particle particle0 = mock(Particle.class);
		when(particle0.getShardables()).thenReturn(Collections.singleton(someAddress));
		Atom atom0 = mock(Atom.class);
		inMemoryAtomStore.store(someAddress, mockStoredAtom(atom0, SpunParticle.up(particle0), someAddress));
		Atom atom1 = mock(Atom.class);
		Particle particle1 = mock(Particle.class);
		when(particle1.getShardables()).thenReturn(Collections.singleton(someAddress));
		inMemoryAtomStore.store(someAddress,
			mockStoredAtom(atom1,
				SpunParticle.down(particle0),
				SpunParticle.up(particle1),
				someAddress
			)
		);
		inMemoryAtomStore.store(someAddress, mockDeletedAtom(atom0, someAddress));

		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(someAddress, null);
		assertThat(upParticles).isEmpty();
	}

	@Test
	public void when_getting_up_particles_from_store_where_collision_occurred_on_soft_state__store_should_return_winner_particle() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		RadixAddress someAddress = mock(RadixAddress.class);
		Particle particle0 = mock(Particle.class);
		when(particle0.getShardables()).thenReturn(Collections.singleton(someAddress));
		Particle particle1 = mock(Particle.class);
		when(particle1.getShardables()).thenReturn(Collections.singleton(someAddress));

		Atom atom0 = Atom.create(
			ParticleGroup.of(
				SpunParticle.down(particle0),
				SpunParticle.up(particle1)
			)
		);
		inMemoryAtomStore.store(someAddress, AtomObservation.softStored(atom0, 0L));

		Particle particle2 = mock(Particle.class);
		when(particle2.getShardables()).thenReturn(Collections.singleton(someAddress));
		Atom atom1 = Atom.create(
			ParticleGroup.of(
				SpunParticle.down(particle0),
				SpunParticle.up(particle2)
			)
		);
		inMemoryAtomStore.store(someAddress, AtomObservation.stored(atom1, 0L));
		Stream<Particle> upParticles = inMemoryAtomStore.getUpParticles(someAddress, null);
		assertThat(upParticles).containsExactly(particle1);
	}
}
