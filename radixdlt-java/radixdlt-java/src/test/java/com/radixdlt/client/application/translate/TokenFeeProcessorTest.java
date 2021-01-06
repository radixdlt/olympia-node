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

package com.radixdlt.client.application.translate;

import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TokenFeeProcessorTest {

	@Test
	public void testTokenFeeProcessor() {
		RRI tokenRri = mock(RRI.class);
		FeeTable feeTable = FeeTable.from(UInt256.TEN, ImmutableList.of());
		RadixAddress address = mock(RadixAddress.class);
		ActionProcessor actionProcessor = mock(ActionProcessor.class);

		TokenFeeProcessor tfp = new TokenFeeProcessor(tokenRri, feeTable);

		Atom atom = Atom.create(ImmutableList.of());

		tfp.process(actionProcessor, address, atom, Optional.empty());

		verify(actionProcessor, times(1)).process(any());
	}
}
