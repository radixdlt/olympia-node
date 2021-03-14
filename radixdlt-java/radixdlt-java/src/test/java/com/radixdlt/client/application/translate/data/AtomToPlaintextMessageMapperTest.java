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

package com.radixdlt.client.application.translate.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.atom.Atom;
import org.junit.Test;

import com.radixdlt.identifiers.AID;

public class AtomToPlaintextMessageMapperTest {
	@Test
	public void testNoMessage() {
		final var mapper = new AtomToPlaintextMessageMapper();
		final var identity = mock(RadixIdentity.class);
		final var atom = mock(Atom.class);
		final var aid = mock(AID.class);
		when(atom.getAid()).thenReturn(aid);
		when(atom.getMessage()).thenReturn(null);

		final var result = mapper.map(atom, identity);

		final var resultsList = ImmutableList.copyOf(result.blockingIterable());
		assertThat(resultsList).isEmpty();
	}

	@Test
	public void testWithMessage() {
		final var message = "test message";
		final var mapper = new AtomToPlaintextMessageMapper();
		final var identity = mock(RadixIdentity.class);
		final var atom = mock(Atom.class);
		final var aid = mock(AID.class);
		when(atom.getAid()).thenReturn(aid);
		when(atom.getMessage()).thenReturn(message);

		final var result = mapper.map(atom, identity);

		final var resultsList = ImmutableList.copyOf(result.blockingIterable());
		assertThat(resultsList)
			.hasSize(1)
			.element(0)
			.matches(ptm -> ptm.getAtomId().equals(aid))
			.matches(ptm -> ptm.getMessage().equals(message));
	}
}