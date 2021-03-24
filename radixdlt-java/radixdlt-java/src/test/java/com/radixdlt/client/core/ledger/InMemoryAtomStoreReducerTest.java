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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import org.junit.Test;

public class InMemoryAtomStoreReducerTest {
	@Test
	public void when_fetch_atoms_head_action_received__head_should_be_received_by_store() {
		InMemoryAtomStore atomStore = mock(InMemoryAtomStore.class);
		InMemoryAtomStoreReducer reducer = new InMemoryAtomStoreReducer(atomStore);
		RadixAddress address = mock(RadixAddress.class);
		RadixNode node = mock(RadixNode.class);
		reducer.reduce(FetchAtomsObservationAction.of("hi", address, node, AtomObservation.head()));

		verify(atomStore, times(1)).store(eq(address), argThat(AtomObservation::isHead));
	}
}