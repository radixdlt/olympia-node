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

import java.math.BigDecimal;
import java.util.Optional;

import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.identifiers.RadixAddress;

/**
 * Interface for processing fees.
 */
public interface FeeProcessor {
	/**
	 * Processes actions in the context of fee generation.
	 *
	 * @param actionProcessor An {@link ActionProcessor} to use to process any actions associated with the fee
	 * @param address The address of the fee payee
	 * @param feelessAtom The atom, without fees, for which to generate fees
	 * @param optionalFee The fee proposed by the client, if present
	 */
	void process(
		ActionProcessor actionProcessor,
		RadixAddress address,
		AtomBuilder feelessAtom,
		Optional<BigDecimal> optionalFee
	);
}
