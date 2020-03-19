package com.radixdlt.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import java.util.Objects;

/**
 * A collection of <a href="https://en.wikipedia.org/wiki/
 * Elliptic_Curve_Digital_Signature_Algorithm">ECDSA</a> signatures.
 */
@SerializerId2("crypto.ecdsa_signatures")
public class ECDSASignatures implements Signatures {
    // Placeholder for the serializer ID
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(DsonOutput.Output.ALL)
    private SerializerDummy serializer = SerializerDummy.DUMMY;

    @JsonProperty("version")
    @DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

    private ImmutableMap<ECPublicKey, ECDSASignature> keyToSignature;

    public ECDSASignatures() {
        this.keyToSignature = ImmutableMap.of();
    }

    public ECDSASignatures(ECPublicKey publicKey, ECDSASignature signature) {
        this.keyToSignature = ImmutableMap.of(publicKey, signature);
    }

    /**
     * Returns a new instance containing {@code keyToSignature}.
     * @param keyToSignature The map of {@link ECDSASignature}s and their corresponding {@link ECPublicKey}
     */
    public ECDSASignatures(ImmutableMap<ECPublicKey, ECDSASignature> keyToSignature) {
    	this.keyToSignature = keyToSignature;
    }

    @Override
    public SignatureScheme signatureScheme() {
        return SignatureScheme.ECDSA;
    }

    @Override
    public boolean isEmpty() {
        return this.keyToSignature.isEmpty();
    }

    @Override
    public int count() {
    	return this.keyToSignature.size();
    }

    @Override
    public Signatures concatenate(ECPublicKey publicKey, Signature signature) {
		if (!(signature instanceof ECDSASignature)) {
		    throw new IllegalArgumentException(
		        String.format("Expected 'signature' to be of type '%s' but got '%s'",
	                ECDSASignature.class.getName(), signature.getClass().getName()
	            )
	        );
		}
		ImmutableMap.Builder<ECPublicKey, ECDSASignature> builder = ImmutableMap.builder();
		builder.putAll(this.keyToSignature);
		builder.put(publicKey, (ECDSASignature) signature);
		return new ECDSASignatures(builder.build());
    }

	@Override
    public boolean hasSignedMessage(Hash message, int requiredMinimumNumberOfValidSignatures) {
        long numberOfValidSignatures = this.keyToSignature.entrySet().stream()
            .filter(e -> e.getKey().verify(message, e.getValue()))
            .count();

        return numberOfValidSignatures >= requiredMinimumNumberOfValidSignatures;
    }

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.keyToSignature);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ECDSASignatures that = (ECDSASignatures) o;
		return Objects.equals(keyToSignature, that.keyToSignature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(keyToSignature);
	}

	@JsonProperty("signatures")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, ECDSASignature> getSerializerSignatures() {
		if (this.keyToSignature != null) {
			return this.keyToSignature.entrySet().stream()
				.collect(Collectors.toMap(e -> encodePublicKey(e.getKey()), Map.Entry::getValue));
		}
		return null;
	}

	@JsonProperty("signatures")
	private void setSerializerSignatures(Map<String, ECDSASignature> signatures) {
		if (signatures != null) {
			this.keyToSignature = signatures.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> decodePublicKey(e.getKey()), Map.Entry::getValue));
		} else {
			this.keyToSignature = null;
		}
	}

	private String encodePublicKey(ECPublicKey key) {
		return Bytes.toHexString(key.getBytes());
	}

	private ECPublicKey decodePublicKey(String str) {
		try {
			return new ECPublicKey(Bytes.fromHexString(str));
		} catch (CryptoException e) {
			throw new IllegalStateException("Error decoding public key", e);
		}
	}
}
