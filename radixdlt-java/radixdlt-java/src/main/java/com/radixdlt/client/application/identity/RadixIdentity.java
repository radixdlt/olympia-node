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

package com.radixdlt.client.application.identity;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.crypto.ECPublicKey;
import io.reactivex.Single;

public interface RadixIdentity {
	Single<Atom> addSignature(TxLowLevelBuilder atom);

	/**
	 * Transforms a possibly encrypted bytes object into an unencrypted one.
	 * If decryption fails then return an empty Maybe.
	 *
	 * @param data bytes to transform
	 * @return either the unencrypted version of the bytes or an error
	 */
	Single<UnencryptedData> decrypt(Data data);

	ECPublicKey getPublicKey();
}
