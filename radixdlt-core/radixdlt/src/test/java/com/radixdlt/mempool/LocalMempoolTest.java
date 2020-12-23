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

import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class LocalMempoolTest {
	private LocalMempool mempool;

	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	@Before
	public void setUp() {
		this.mempool = new LocalMempool(2, hasher);
	}

	@Test(expected = IllegalArgumentException.class)
	public void when_constructing_with_negative_size__then_exception_is_thrown() {
		assertNotNull(new LocalMempool(-1, hasher));
		fail();
	}

	@Test(expected = MempoolDuplicateException.class)
	public void when_adding_atom_with_same_aid__then_exception_is_thrown()
		throws MempoolFullException, MempoolDuplicateException {
		Command command = mock(Command.class);

		this.mempool.add(command);
		this.mempool.add(command);
		fail();
	}

	@Test
	public void when_adding_too_many_atoms__then_exception_is_thrown()
		throws MempoolFullException, MempoolDuplicateException {
		this.mempool.add(makeCommand(1));
		this.mempool.add(makeCommand(2));
		Command badCommand = makeCommand(3);
		try {
			this.mempool.add(badCommand);
			fail();
		} catch (MempoolFullException e) {
			assertSame(badCommand, e.command());
		}
	}

	@Test
	public void when_committed_atom_is_removed__then_mempool_size_decreases()
		throws MempoolFullException, MempoolDuplicateException {
		Command command = makeCommand(1234);
		this.mempool.add(command);
		assertEquals(1, this.mempool.count());
		this.mempool.removeCommitted(hasher.hash(command));
		assertEquals(0, this.mempool.count());
	}

	@Test
	public void when_rejected_atom_is_removed__then_mempool_size_decreases()
		throws MempoolFullException, MempoolDuplicateException {
		Command command = makeCommand(1234);
		this.mempool.add(command);
		assertEquals(1, this.mempool.count());
		this.mempool.removeRejected(hasher.hash(command));
		assertEquals(0, this.mempool.count());
	}

	@Test
	public void when_an_atom_is_requested__then_mempool_returns_and_retains_atoms()
		throws MempoolFullException, MempoolDuplicateException {
		Command command = makeCommand(1234);
		this.mempool.add(command);
		assertEquals(1, this.mempool.count()); // precondition

		List<Command> commands = this.mempool.getCommands(1, Sets.newHashSet());
		assertEquals(1, commands.size());
		assertSame(command, commands.get(0));
		assertEquals(1, this.mempool.count()); // postcondition
	}

	@Test
	public void when_too_many_atoms_requested__then_mempool_returns_fewer_atoms()
		throws MempoolFullException, MempoolDuplicateException {
		Command command = makeCommand(1234);
		this.mempool.add(command);
		assertEquals(1, this.mempool.count()); // precondition

		List<Command> commands = this.mempool.getCommands(2, Sets.newHashSet());
		assertEquals(1, commands.size());
		assertSame(command, commands.get(0));
		assertEquals(1, this.mempool.count()); // postcondition
	}

	@Test
	public void when_atoms_requested_from_empty_mempool__then_mempool_returns_empty_list() {
		assertEquals(0, this.mempool.count()); // precondition

		List<Command> commands = this.mempool.getCommands(1, Sets.newHashSet());
		assertTrue(commands.isEmpty());
		assertEquals(0, this.mempool.count()); // postcondition
	}

	@Test
	public void when_an_atom_is_requested__then_mempool_excludes_atoms()
		throws MempoolFullException, MempoolDuplicateException {
		Command command = makeCommand(1234);
		this.mempool.add(command);
		assertEquals(1, this.mempool.count()); // precondition

		List<Command> commands = this.mempool.getCommands(1, Sets.newHashSet(hasher.hash(command)));
		assertTrue(commands.isEmpty());
		assertEquals(1, this.mempool.count()); // postcondition
	}

	@Test
	public void when_max_count_called__max_count_returned() {
		assertEquals(2, this.mempool.maxCount());
	}

	@Test
	public void well_formatted_tostring()
		throws MempoolFullException, MempoolDuplicateException {
		this.mempool.add(makeCommand(1234));
		assertEquals(1, this.mempool.count()); // precondition

		String tostring = this.mempool.toString();

		assertThat(tostring, containsString("1/2"));
		assertThat(tostring, containsString(LocalMempool.class.getSimpleName()));
	}

	private static Command makeCommand(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return new Command(temp);
	}
}
