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

package com.radixdlt.client.core.network.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;
import com.radixdlt.identifiers.EUID;

public class FetchAtomsRequestActionTest {
	@Test
	public void when_initializing_with_null__npe_should_be_thrown() {
		assertThatThrownBy(() -> FetchAtomsRequestAction.newRequest(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	public void when_initializing_with_address__shards_should_match() {
		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(EUID.ONE);
		assertThat(FetchAtomsRequestAction.newRequest(address).getShards()).containsExactly(EUID.ONE.getShard());
	}
}