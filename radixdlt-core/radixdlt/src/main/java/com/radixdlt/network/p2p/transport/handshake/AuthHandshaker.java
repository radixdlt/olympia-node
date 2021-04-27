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

package com.radixdlt.network.p2p.transport.handshake;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECIESCoder;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Pair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.radixdlt.crypto.HashUtils.kec256;
import static com.radixdlt.utils.Bytes.bigIntegerToBytes;
import static com.radixdlt.utils.Bytes.xor;

/**
 * Handles the auth handshake to create an encrypted communication channel between peers.
 */
public final class AuthHandshaker {
	private static final int NONCE_SIZE = 32;
	private static final int MIN_PADDING = 100;
	private static final int MAX_PADDING = 300;
	private static final int SECRET_SIZE = 32;
	private static final int MAC_SIZE = 256;

	private final Serialization serialization;
	private final SecureRandom secureRandom;
	private final ECKeyPair nodeKey;
	private final byte[] nonce;
	private final ECKeyPair ephemeralKey;
	private boolean isInitiator = false;
	private Optional<byte[]> initiatePacketOpt = Optional.empty();
	private Optional<byte[]> responsePacketOpt = Optional.empty();
	private Optional<ECPublicKey> remotePubKeyOpt;

	public AuthHandshaker(Serialization serialization, SecureRandom secureRandom, ECKeyPair nodeKey) {
		this.serialization = Objects.requireNonNull(serialization);
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.nodeKey = Objects.requireNonNull(nodeKey);
		this.nonce = randomBytes(NONCE_SIZE);
		this.ephemeralKey = ECKeyPair.generateNew();
	}

	public byte[] initiate(ECPublicKey remotePubKey) throws PublicKeyException {
		final var message = createAuthInitiateMessage(remotePubKey);
		final var encoded = serialization.toDson(message, DsonOutput.Output.WIRE);
		final var padding = randomBytes(secureRandom.nextInt(MAX_PADDING - MIN_PADDING) + MIN_PADDING);
		final var padded = new byte[encoded.length + padding.length];
		System.arraycopy(encoded, 0, padded, 0, encoded.length);
		System.arraycopy(padding, 0, padded, encoded.length, padding.length);

		final var encryptedSize = padded.length + ECIESCoder.OVERHEAD_SIZE;
		final var sizePrefix = ByteBuffer.allocate(2).putShort((short) encryptedSize).array();
		final var encryptedPayload = ECIESCoder.encrypt(remotePubKey.getEcPoint(), padded, sizePrefix);
		final var packet = new byte[sizePrefix.length + encryptedPayload.length];
		System.arraycopy(sizePrefix, 0, packet, 0, sizePrefix.length);
		System.arraycopy(encryptedPayload, 0, packet, sizePrefix.length, encryptedPayload.length);

		this.isInitiator = true;
		this.initiatePacketOpt = Optional.of(packet);
		this.remotePubKeyOpt = Optional.of(remotePubKey);

		return packet;
	}

	private AuthInitiateMessage createAuthInitiateMessage(ECPublicKey remotePubKey) {
		final var agreement = new ECDHBasicAgreement();
		agreement.init(new ECPrivateKeyParameters(new BigInteger(1, nodeKey.getPrivateKey()), ECKeyUtils.domain()));
		final var sharedSecret =
			bigIntegerToBytes(
				agreement.calculateAgreement(new ECPublicKeyParameters(remotePubKey.getEcPoint(), ECKeyUtils.domain())),
				NONCE_SIZE
			);
		final var messageToSign = xor(sharedSecret, nonce);
		final var signature = ephemeralKey.sign(messageToSign);
		return new AuthInitiateMessage(
			signature,
			HashCode.fromBytes(nodeKey.getPublicKey().getBytes()),
			HashCode.fromBytes(nonce)
		);
	}

	public Pair<byte[], AuthHandshakeResult> handleInitialMessage(byte[] data)
			throws IOException, InvalidCipherTextException, PublicKeyException {
		final var sizeBytes = Arrays.copyOfRange(data, 0, 2);
		final var encryptedPayload = Arrays.copyOfRange(data, 2, data.length);

		final var plaintext = ECIESCoder.decrypt(
			new BigInteger(1, nodeKey.getPrivateKey()),
			encryptedPayload,
			sizeBytes
		);

		final var message = serialization.fromDson(plaintext, AuthInitiateMessage.class);

		final var response = new AuthResponseMessage(
			HashCode.fromBytes(ephemeralKey.getPublicKey().getBytes()),
			HashCode.fromBytes(nonce)
		);
		final var encodedResponse = serialization.toDson(response, DsonOutput.Output.WIRE);

		final var encryptedSize = encodedResponse.length + ECIESCoder.OVERHEAD_SIZE;
		final var sizePrefix = ByteBuffer.allocate(2).putShort((short) encryptedSize).array();
		final var remotePubKey = ECPublicKey.fromBytes(message.getPublicKey().asBytes());
		final var encryptedResponsePayload =
			ECIESCoder.encrypt(remotePubKey.getEcPoint(), encodedResponse, sizePrefix);
		final var packet = new byte[sizePrefix.length + encryptedResponsePayload.length];
		System.arraycopy(sizePrefix, 0, packet, 0, sizePrefix.length);
		System.arraycopy(encryptedResponsePayload, 0, packet, sizePrefix.length, encryptedResponsePayload.length);

		final var remoteEphemeralKey = extractEphemeralKey(
			message.getSignature(),
			message.getNonce(),
			remotePubKey
		);

		this.initiatePacketOpt = Optional.of(data);
		this.responsePacketOpt = Optional.of(packet);
		this.remotePubKeyOpt = Optional.of(remotePubKey);

		final var handshakreResult = finalizeHandshake(remoteEphemeralKey, message.getNonce());

		return Pair.of(packet, handshakreResult);
	}

	public AuthHandshakeResult handleResponseMessage(byte[] data) throws IOException, InvalidCipherTextException, PublicKeyException {
		final var sizeBytes = Arrays.copyOfRange(data, 0, 2);
		final var encryptedPayload = Arrays.copyOfRange(data, 2, data.length);

		final var plaintext = ECIESCoder.decrypt(
			new BigInteger(1, nodeKey.getPrivateKey()),
			encryptedPayload,
			sizeBytes
		);

		final var message = serialization.fromDson(plaintext, AuthResponseMessage.class);

		this.responsePacketOpt = Optional.of(data);

		final var remoteEphemeralKey = ECPublicKey.fromBytes(message.getEphemeralPublicKey().asBytes());
		return finalizeHandshake(remoteEphemeralKey, message.getNonce());
	}

	private ECPublicKey extractEphemeralKey(ECDSASignature signature, HashCode nonce, ECPublicKey publicKey) {
		final var agreement = new ECDHBasicAgreement();
		agreement.init(new ECPrivateKeyParameters(new BigInteger(1, nodeKey.getPrivateKey()), ECKeyUtils.domain()));
		final var sharedSecret = agreement.calculateAgreement(new ECPublicKeyParameters(publicKey.getEcPoint(), ECKeyUtils.domain()));
		final var token = bigIntegerToBytes(sharedSecret, NONCE_SIZE);
		final var signed = xor(token, nonce.asBytes());
		return ECPublicKey.recoverFrom(HashCode.fromBytes(signed), signature).orElseThrow();
	}

	private AuthHandshakeResult finalizeHandshake(ECPublicKey remoteEphemeralKey, HashCode remoteNonce) {
		final var initiatePacket = initiatePacketOpt.get();
		final var responsePacket = responsePacketOpt.get();
		final var remotePubKey = remotePubKeyOpt.get();

		final var agreement = new ECDHBasicAgreement();
		agreement.init(new ECPrivateKeyParameters(new BigInteger(1, ephemeralKey.getPrivateKey()), ECKeyUtils.domain()));
		final var secretScalar = agreement.calculateAgreement(
			new ECPublicKeyParameters(remoteEphemeralKey.getEcPoint(),
			ECKeyUtils.domain())
		);
		final var agreedSecret = bigIntegerToBytes(secretScalar, SECRET_SIZE);

		final var sharedSecret = isInitiator
			? kec256(agreedSecret, kec256(remoteNonce.asBytes(), nonce))
			: kec256(agreedSecret, kec256(nonce, remoteNonce.asBytes()));

		final var aesSecret = kec256(agreedSecret, sharedSecret);

		final var macSecrets = isInitiator
			? macSecretSetup(agreedSecret, aesSecret, initiatePacket, nonce, responsePacket, remoteNonce.asBytes())
			: macSecretSetup(agreedSecret, aesSecret, initiatePacket, remoteNonce.asBytes(), responsePacket, nonce);

		final var secrets = new Secrets(
			aesSecret,
			kec256(agreedSecret, aesSecret),
			kec256(sharedSecret),
			macSecrets.getFirst(),
			macSecrets.getSecond()
		);

		return AuthHandshakeResult.create(remotePubKey, secrets);
	}

	private Pair<KeccakDigest, KeccakDigest> macSecretSetup(
		byte[] agreedSecret,
		byte[] aesSecret,
		byte[] initiatePacket,
		byte[] initiateNonce,
		byte[] responsePacket,
		byte[] responseNonce
	) {
		final var macSecret = kec256(agreedSecret, aesSecret);
		final var mac1 = new KeccakDigest(MAC_SIZE);
		mac1.update(xor(macSecret, responseNonce), 0, macSecret.length);
		final var bufSize = 32;
		final var buf = new byte[bufSize];
		new KeccakDigest(mac1).doFinal(buf, 0);
		mac1.update(initiatePacket, 0, initiatePacket.length);
		new KeccakDigest(mac1).doFinal(buf, 0);
		final var mac2 = new KeccakDigest(MAC_SIZE);
		mac2.update(xor(macSecret, initiateNonce), 0, macSecret.length);
		new KeccakDigest(mac2).doFinal(buf, 0);
		mac2.update(responsePacket, 0, responsePacket.length);
		new KeccakDigest(mac2).doFinal(buf, 0);
		return isInitiator ? Pair.of(mac1, mac2) : Pair.of(mac2, mac1);
	}

	private byte[] randomBytes(int len) {
		final var arr = new byte[len];
		secureRandom.nextBytes(arr);
		return arr;
	}
}
