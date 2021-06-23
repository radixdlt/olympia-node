/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib.dto;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.util.encoders.Hex;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class FinalizedTransaction {
	private final byte[] blob;
	private final ECDSASignature signature;
	private final ECPublicKey publicKey;
	private final AID txId;

	private FinalizedTransaction(byte[] blob, ECDSASignature signature, ECPublicKey publicKey, AID txId) {
		this.blob = blob;
		this.signature = signature;
		this.publicKey = publicKey;
		this.txId = txId;
	}

	public static FinalizedTransaction create(byte[] blob, ECDSASignature signature, ECPublicKey publicKey, AID txId) {
		requireNonNull(blob);
		requireNonNull(signature);
		requireNonNull(publicKey);

		return new FinalizedTransaction(blob, signature, publicKey, txId);
	}

	public static FinalizedTransaction create(BuiltTransaction tx, ECKeyPair keyPair) {
		requireNonNull(tx);
		requireNonNull(keyPair);

		var signature = keyPair.sign(tx.getTransaction().getHashToSign());

		return FinalizedTransaction.create(tx.getTransaction().getBlob(), signature, keyPair.getPublicKey(), null);
	}

	public FinalizedTransaction withTxId(AID txId) {
		return create(blob, signature, publicKey, txId);
	}

	@JsonProperty("blob")
	public Blob getBlob() {
		return new Blob(blob);
	}

	@JsonProperty("signatureDER")
	public String getSignature() {
		return encodeToDer(signature);
	}

	@JsonProperty("publicKeyOfSigner")
	public String getPublicKey() {
		return publicKey.toHex();
	}

	@JsonProperty("txID")
	public String getTxId() {
		return Optional.ofNullable(txId)
			.map(AID::toString)
			.orElse(null);
	}

	private static String encodeToDer(ECDSASignature signature) {
		try {
			ASN1EncodableVector vector = new ASN1EncodableVector();
			vector.add(new ASN1Integer(signature.getR()));
			vector.add(new ASN1Integer(signature.getS()));

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ASN1OutputStream asnOS = ASN1OutputStream.create(baos);

			asnOS.writeObject(new DERSequence(vector));
			asnOS.flush();

			return Hex.toHexString(baos.toByteArray());
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to encode to DER signature: " + signature);
		}
	}

	public Optional<Object> rawTxId() {
		return Optional.ofNullable(txId);
	}

	private static class Blob {
		private final byte[] blob;

		private Blob(byte[] blob) {
			this.blob = blob;
		}

		@JsonProperty("blob")
		public String toJson() {
			return Hex.toHexString(blob);
		}
	}
}
