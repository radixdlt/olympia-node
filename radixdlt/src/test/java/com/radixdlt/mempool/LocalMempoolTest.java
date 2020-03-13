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

package com.radixdlt.mempool;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class LocalMempoolTest {

	private RuntimeProperties config;
	private LocalMempool mempool;

	@Before
	public void setUp() {
		this.config = mock(RuntimeProperties.class);
		when(this.config.get(eq("mempool.maxSize"), anyInt())).thenReturn(2);

		// test module to hook up dependencies
		Module testModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(RuntimeProperties.class).toInstance(config);
			}
		};

		Injector injector = Guice.createInjector(testModule);
		this.mempool = injector.getInstance(LocalMempool.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void when_constructing_with_negative_size__then_exception_is_thrown() {
		assertNotNull(new LocalMempool(-1));
		fail();
	}

	@Test(expected = MempoolDuplicateException.class)
	public void when_adding_atom_with_same_aid__then_exception_is_thrown()
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = mock(Atom.class);
		when(atom.getAID()).thenReturn(AID.ZERO);

		this.mempool.addAtom(atom);
		this.mempool.addAtom(atom);
		fail();
	}

	@Test
	public void when_adding_too_many_atoms__then_exception_is_thrown()
		throws MempoolFullException, MempoolDuplicateException {
		this.mempool.addAtom(makeAtom(1));
		this.mempool.addAtom(makeAtom(2));
		Atom badAtom = makeAtom(3);
		try {
			this.mempool.addAtom(badAtom);
			fail();
		} catch (MempoolFullException e) {
			assertSame(badAtom, e.atom());
		}
	}

	@Test
	public void when_committed_atom_is_removed__then_mempool_size_decreases()
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = makeAtom(1234);
		this.mempool.addAtom(atom);
		assertEquals(1, this.mempool.atomCount());
		this.mempool.removeCommittedAtom(atom.getAID());
		assertEquals(0, this.mempool.atomCount());
	}

	@Test
	public void when_rejected_atom_is_removed__then_mempool_size_decreases()
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = makeAtom(1234);
		this.mempool.addAtom(atom);
		assertEquals(1, this.mempool.atomCount());
		this.mempool.removeRejectedAtom(atom.getAID());
		assertEquals(0, this.mempool.atomCount());
	}

	@Test
	public void when_an_atom_is_requested__then_mempool_returns_and_retains_atoms()
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = makeAtom(1234);
		this.mempool.addAtom(atom);
		assertEquals(1, this.mempool.atomCount()); // precondition

		List<Atom> atoms = this.mempool.getAtoms(1, Sets.newHashSet());
		assertEquals(1, atoms.size());
		assertSame(atom, atoms.get(0));
		assertEquals(1, this.mempool.atomCount()); // postcondition
	}

	@Test
	public void when_too_many_atoms_requested__then_mempool_returns_fewer_atoms()
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = makeAtom(1234);
		this.mempool.addAtom(atom);
		assertEquals(1, this.mempool.atomCount()); // precondition

		List<Atom> atoms = this.mempool.getAtoms(2, Sets.newHashSet());
		assertEquals(1, atoms.size());
		assertSame(atom, atoms.get(0));
		assertEquals(1, this.mempool.atomCount()); // postcondition
	}

	@Test
	public void when_atoms_requested_from_empty_mempool__then_mempool_returns_empty_list() {
		assertEquals(0, this.mempool.atomCount()); // precondition

		List<Atom> atoms = this.mempool.getAtoms(1, Sets.newHashSet());
		assertTrue(atoms.isEmpty());
		assertEquals(0, this.mempool.atomCount()); // postcondition
	}

	@Test
	public void when_an_atom_is_requested__then_mempool_excludes_atoms()
		throws MempoolFullException, MempoolDuplicateException {
		Atom atom = makeAtom(1234);
		this.mempool.addAtom(atom);
		assertEquals(1, this.mempool.atomCount()); // precondition

		List<Atom> atoms = this.mempool.getAtoms(1, Sets.newHashSet(atom.getAID()));
		assertTrue(atoms.isEmpty());
		assertEquals(1, this.mempool.atomCount()); // postcondition
	}

	@Test
	public void when_max_count_called__max_count_returned() {
		assertEquals(2, this.mempool.maxCount());
	}

	@Test
	public void well_formatted_tostring()
		throws MempoolFullException, MempoolDuplicateException {
		this.mempool.addAtom(makeAtom(1234));
		assertEquals(1, this.mempool.atomCount()); // precondition

		String tostring = this.mempool.toString();

		assertThat(tostring, containsString("1/2"));
		assertThat(tostring, containsString(LocalMempool.class.getSimpleName()));
	}

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	private static Atom makeAtom(int n) {
		Atom atom = mock(Atom.class);
		when(atom.getAID()).thenReturn(makeAID(n));
		return atom;
	}
}
