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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.HashUtils;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.utils.Ints;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SharedMempoolTest {
	private static final HashCode TEST_HASH = makeHash(1234);

	private LocalMempool localMempool;
	private MempoolNetworkTx mempoolNetworkTx;
	private SystemCounters counters;
	private Mempool sharedMempool;

	@Before
	public void setUp() {
		this.localMempool = mock(LocalMempool.class);
		this.mempoolNetworkTx = mock(MempoolNetworkTx.class);
		this.counters = mock(SystemCounters.class);
		this.sharedMempool = new SharedMempool(counters, localMempool, mempoolNetworkTx);
	}

	@Test
	public void when_adding_atom__then_atom_is_added_and_sent()
		throws MempoolFullException, MempoolDuplicateException {
		Command mockCommand = mock(Command.class);
		this.sharedMempool.add(mockCommand);
		verify(this.localMempool, times(1)).add(any());
		verify(this.mempoolNetworkTx, times(1)).sendMempoolSubmission(any());
	}

	@Test
	public void when_committed_atom_is_removed__then_local_mempool_removed() {
		this.sharedMempool.removeCommitted(TEST_HASH);
		verify(this.localMempool, times(1)).removeCommitted(TEST_HASH);
	}

	@Test
	public void when_rejected_atom_is_removed__then_local_mempool_removed() {
		this.sharedMempool.removeRejected(TEST_HASH);
		verify(this.localMempool, times(1)).removeRejected(TEST_HASH);
	}

	@Test
	public void when_atoms_requested__then_local_mempool_called() {
		Set<HashCode> seen = Sets.newHashSet();
		this.sharedMempool.getCommands(1, seen);
		verify(this.localMempool, times(1)).getCommands(eq(1), same(seen));
	}

	@Test
	public void when_an_atom__count_is_requested__then_local_mempool_called() {
		this.sharedMempool.count();
		verify(this.localMempool, times(1)).count();
	}

	@Test
	public void well_formatted_tostring() {
		when(this.localMempool.count()).thenReturn(1);
		when(this.localMempool.maxCount()).thenReturn(2);

		String tostring = this.sharedMempool.toString();

		assertThat(tostring).contains("1/2");
		assertThat(tostring).contains(SharedMempool.class.getSimpleName());
	}

	private static HashCode makeHash(int n) {
		byte[] temp = new byte[256];
		Ints.copyTo(n, temp, 256 - Integer.BYTES);
		return HashUtils.sha256(temp);
	}
}
