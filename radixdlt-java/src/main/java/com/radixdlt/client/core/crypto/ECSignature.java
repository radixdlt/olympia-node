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

package com.radixdlt.client.core.crypto;

import java.io.IOException;
import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.util.encoders.Base64;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;
import org.radix.utils.primitives.Bytes;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("crypto.ecdsa_signature")
public class ECSignature extends SerializableObject {
	private byte[] r;
	private byte[] s;

	ECSignature() {
		// No-arg constructor for serializer
	}

	public ECSignature(BigInteger r, BigInteger s) {
		this.r = Bytes.trimLeadingZeros(r.toByteArray());
		this.s = Bytes.trimLeadingZeros(s.toByteArray());
	}

	public String getRBase64() {
		return Base64.toBase64String(r);
	}

	public BigInteger getR() {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		return new BigInteger(1, r);
	}

	public BigInteger getS() {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		return new BigInteger(1, s);
	}

	@JsonProperty("r")
	@DsonOutput(Output.ALL)
	private byte[] getJsonR() {
		return r;
	}

	@JsonProperty("s")
	@DsonOutput(Output.ALL)
	private byte[] getJsonS() {
		return s;
	}

	@JsonProperty("r")
	private void setJsonR(byte[] r) {
		this.r = r.clone();
	}

	@JsonProperty("s")
	private void setJsonS(byte[] s) {
		this.s = s.clone();
	}

	public static ECSignature decodeFromDER(byte[] bytes) {
		DLSequence seq;
		ASN1Integer r, s;
		try (ASN1InputStream decoder = new ASN1InputStream(bytes)) {
			seq = (DLSequence) decoder.readObject();
			r = (ASN1Integer) seq.getObjectAt(0);
			s = (ASN1Integer) seq.getObjectAt(1);
		} catch (ClassCastException | IOException e) {
			throw new IllegalArgumentException(e);
		}

		return new ECSignature(r.getPositiveValue(), s.getPositiveValue());
	}
}
