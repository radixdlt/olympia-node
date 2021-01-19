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

package com.radixdlt.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DLSequence;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;

/**
 * An <a href="https://en.wikipedia.org/wiki/
 * Elliptic_Curve_Digital_Signature_Algorithm">ECDSA</a> signature represented as
 * a tuple of {@link BigInteger}s {@code (R, S)}/
 */
@SerializerId2("crypto.ecdsa_signature")
public final class ECDSASignature implements Signature {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	/* The two components of the signature. */
	private BigInteger r;
	private BigInteger s;

	public ECDSASignature() {
		this(BigInteger.ZERO, BigInteger.ZERO);
	}

	/**
     * Constructs a signature with the given components. Does NOT automatically canonicalise the signature.
     */
	public ECDSASignature(BigInteger r, BigInteger s) {
    	super();

    	this.r = Objects.requireNonNull(r);
        this.s = Objects.requireNonNull(s);
    }

	public BigInteger getR() {
		return r;
	}

	public BigInteger getS() {
		return s;
	}

	@JsonProperty("r")
	@DsonOutput(Output.ALL)
	private byte[] getJsonR() {
		return Bytes.trimLeadingZeros(r.toByteArray());
	}

	@JsonProperty("s")
	@DsonOutput(Output.ALL)
	private byte[] getJsonS() {
		return Bytes.trimLeadingZeros(s.toByteArray());
	}

	@JsonProperty("r")
	private void setJsonR(byte[] r) {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		this.r = new BigInteger(1, r);
	}

	@JsonProperty("s")
	private void setJsonS(byte[] s) {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		this.s = new BigInteger(1, s);
	}

	@Override
	public String toString() {
		return toHexString();
	}

	public String toHexString() {
		return Bytes.toHexString(getJsonR()) + Bytes.toHexString(getJsonS());
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (o instanceof ECDSASignature) {
			ECDSASignature signature = (ECDSASignature) o;
			return Objects.equals(r, signature.r)
				&& Objects.equals(s, signature.s);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(r, s);
	}

	@Override
	public SignatureScheme signatureScheme() {
		return SignatureScheme.ECDSA;
	}

	public static ECDSASignature decodeFromDER(byte[] bytes) {
		try (ASN1InputStream decoder = new ASN1InputStream(bytes)) {
			DLSequence seq = (DLSequence) decoder.readObject();
			ASN1Integer r = (ASN1Integer) seq.getObjectAt(0);
			ASN1Integer s = (ASN1Integer) seq.getObjectAt(1);
			return new ECDSASignature(r.getPositiveValue(), s.getPositiveValue());
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read bytes as ASN1 decode bytes", e);
		} catch (ClassCastException e) {
			throw new IllegalStateException("Failed to cast to ASN1Integer", e);
		}

	}
}
