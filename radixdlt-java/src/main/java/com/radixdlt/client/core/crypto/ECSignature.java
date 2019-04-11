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

@SerializerId2("SIGNATURE")
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

	public static ECSignature fromASN1(byte[] bytes) {
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
