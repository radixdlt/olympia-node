package com.radixdlt.client.core.crypto;

import java.math.BigInteger;

import org.bouncycastle.util.encoders.Base64;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("SIGNATURE")
public class ECSignature {
	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	// Placeholder for the serializer ID
	@JsonProperty("serializer")
	@DsonOutput({Output.API, Output.WIRE, Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private byte[] r;
	private byte[] s;

	ECSignature() {
		// No-arg constructor for serializer
	}

	public ECSignature(BigInteger r, BigInteger s) {
		this.r = r.toByteArray();
		this.s = s.toByteArray();
	}

	public String getRBase64() {
		return Base64.toBase64String(r);
	}

	public BigInteger getR() {
		return new BigInteger(r);
	}

	public BigInteger getS() {
		return new BigInteger(s);
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
}
