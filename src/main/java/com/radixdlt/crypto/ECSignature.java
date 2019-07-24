package com.radixdlt.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import java.math.BigInteger;
import java.util.Objects;

@SerializerId2("crypto.ecdsa_signature")
public final class ECSignature {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	/* The two components of the signature. */
	private BigInteger r;
	private BigInteger s;

	public ECSignature() {
		this(BigInteger.ZERO, BigInteger.ZERO);
	}

	/**
     * Constructs a signature with the given components. Does NOT automatically canonicalise the signature.
     */
	public ECSignature(BigInteger r, BigInteger s) {
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

    @Override
	public boolean equals(Object o) {
        if (this == o) {
        	return true;
        }
        if (o instanceof ECSignature) {
        	ECSignature signature = (ECSignature) o;
        	return Objects.equals(this.r, signature.r) && Objects.equals(this.s, signature.s);
        }
        return false;
    }

    @Override
	public int hashCode() {
        int result = r.hashCode();
        result = 31 * result + s.hashCode();
        return result;
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
}
