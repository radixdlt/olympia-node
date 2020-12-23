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

import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import io.reactivex.disposables.Disposable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.radixdlt.identifiers.RadixAddress;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.reactivex.observers.TestObserver;

public class RadixAtomPullerTest {
	@Test
	 public void when_many_subscribers_pull_atoms_on_the_same_address__fetch_atoms_action_should_be_emitted_once() throws Exception {
		RadixNetworkController controller = mock(RadixNetworkController.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(controller);

		List<TestObserver<?>> observers = Stream.iterate(TestObserver.create(), t -> TestObserver.create()).limit(10)
			.collect(Collectors.toList());

		List<Disposable> disposables = observers.stream()
			.map(observer -> radixAtomPuller.pull(address).subscribe())
			.collect(Collectors.toList());

		verify(controller, times(1)).dispatch(argThat(t -> {
			if (!(t instanceof FetchAtomsRequestAction)) {
				return false;
			}

			FetchAtomsRequestAction fetchAtomsAction = (FetchAtomsRequestAction) t;
			return fetchAtomsAction.getAddress().equals(address);
		}));

		disposables.forEach(Disposable::dispose);
	}
}