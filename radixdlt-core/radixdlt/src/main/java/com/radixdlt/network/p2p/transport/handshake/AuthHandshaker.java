/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.p2p.transport.handshake;

import static com.radixdlt.crypto.HashUtils.kec256;
import static com.radixdlt.utils.Bytes.bigIntegerToBytes;
import static com.radixdlt.utils.Bytes.xor;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECIESCoder;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult.AuthHandshakeSuccess;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Pair;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

/** Handles the auth handshake to create an encrypted communication channel between peers. */
public final class AuthHandshaker {
  private static final byte STATUS_OK = 0x01;
  private static final byte STATUS_ERROR = 0x02;

  private static final int NONCE_SIZE = 32;
  private static final int MIN_PADDING = 100;
  private static final int MAX_PADDING = 300;
  private static final int SECRET_SIZE = 32;
  private static final int MAC_SIZE = 256;

  private final Serialization serialization;
  private final SecureRandom secureRandom;
  private final ECKeyOps ecKeyOps;
  private final byte[] nonce;
  private final ECKeyPair ephemeralKey;
  private final int networkId;
  private final String newestForkName;
  private boolean isInitiator = false;
  private Optional<byte[]> initiatePacketOpt = Optional.empty();
  private Optional<byte[]> responsePacketOpt = Optional.empty();
  private Optional<ECPublicKey> remotePubKeyOpt;

  public AuthHandshaker(
      Serialization serialization,
      SecureRandom secureRandom,
      ECKeyOps ecKeyOps,
      int networkId,
      String newestForkName) {
    this.serialization = Objects.requireNonNull(serialization);
    this.secureRandom = Objects.requireNonNull(secureRandom);
    this.ecKeyOps = Objects.requireNonNull(ecKeyOps);
    this.nonce = randomBytes(NONCE_SIZE);
    this.ephemeralKey = ECKeyPair.generateNew();
    this.networkId = networkId;
    this.newestForkName = newestForkName;
  }

  public byte[] initiate(ECPublicKey remotePubKey) {
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
    final var sharedSecret = bigIntegerToBytes(ecKeyOps.ecdhAgreement(remotePubKey), NONCE_SIZE);
    final var messageToSign = xor(sharedSecret, nonce);
    final var signature = ephemeralKey.sign(messageToSign);
    return new AuthInitiateMessage(
        signature,
        HashCode.fromBytes(ecKeyOps.nodePubKey().getBytes()),
        HashCode.fromBytes(nonce),
        networkId,
        Optional.of(newestForkName));
  }

  public Pair<byte[], AuthHandshakeResult> handleInitialMessage(ByteBuf data) {
    try {
      final var sizeBytes = new byte[2];
      data.getBytes(0, sizeBytes, 0, sizeBytes.length);
      final var encryptedPayload = new byte[data.readableBytes() - sizeBytes.length];
      data.getBytes(sizeBytes.length, encryptedPayload, 0, encryptedPayload.length);
      final var plaintext = ecKeyOps.eciesDecrypt(encryptedPayload, sizeBytes);
      final var message = serialization.fromDson(plaintext, AuthInitiateMessage.class);
      final var remotePubKey = ECPublicKey.fromBytes(message.getPublicKey().asBytes());

      if (message.getNetworkId() != this.networkId) {
        return Pair.of(
            new byte[] {STATUS_ERROR},
            AuthHandshakeResult.error(
                String.format(
                    "Network ID mismatch (expected %s, got %s)",
                    this.networkId, message.getNetworkId()),
                Optional.of(NodeId.fromPublicKey(remotePubKey))));
      }

      final var response =
          new AuthResponseMessage(
              HashCode.fromBytes(ephemeralKey.getPublicKey().getBytes()),
              HashCode.fromBytes(nonce),
              Optional.of(newestForkName));
      final var encodedResponse = serialization.toDson(response, DsonOutput.Output.WIRE);

      final var encryptedSize = encodedResponse.length + ECIESCoder.OVERHEAD_SIZE;
      final var sizePrefix = ByteBuffer.allocate(2).putShort((short) encryptedSize).array();
      final var encryptedResponsePayload =
          ECIESCoder.encrypt(remotePubKey.getEcPoint(), encodedResponse, sizePrefix);

      final var packet = new byte[1 + sizePrefix.length + encryptedResponsePayload.length];
      packet[0] = STATUS_OK;
      System.arraycopy(sizePrefix, 0, packet, 1, sizePrefix.length);
      System.arraycopy(
          encryptedResponsePayload,
          0,
          packet,
          1 + sizePrefix.length,
          encryptedResponsePayload.length);

      final var remoteEphemeralKey =
          extractEphemeralKey(message.getSignature(), message.getNonce(), remotePubKey);

      final var initiatePacket = new byte[data.readableBytes()];
      data.getBytes(0, initiatePacket);
      this.initiatePacketOpt = Optional.of(initiatePacket);
      this.responsePacketOpt = Optional.of(packet);
      this.remotePubKeyOpt = Optional.of(remotePubKey);

      final var handshakeResult =
          finalizeHandshake(remoteEphemeralKey, message.getNonce(), message.getNewestForkName());
      return Pair.of(packet, handshakeResult);
    } catch (PublicKeyException | InvalidCipherTextException | IOException ex) {
      return Pair.of(
          new byte[] {STATUS_ERROR},
          AuthHandshakeResult.error(
              String.format("Handshake decryption failed (%s)", ex.getMessage()),
              Optional.empty()));
    }
  }

  public AuthHandshakeResult handleResponseMessage(ByteBuf data) throws IOException {
    try {
      final var statusByte = data.getByte(0);
      if (statusByte != STATUS_OK) {
        return AuthHandshakeResult.error("Received error response", Optional.empty());
      }

      final var sizeBytes = new byte[2];
      data.getBytes(1, sizeBytes, 0, sizeBytes.length);
      final var encryptedPayload = new byte[data.readableBytes() - 3];
      data.getBytes(3, encryptedPayload, 0, encryptedPayload.length);
      final var plaintext = ecKeyOps.eciesDecrypt(encryptedPayload, sizeBytes);
      final var message = serialization.fromDson(plaintext, AuthResponseMessage.class);
      final var responsePacket = new byte[data.readableBytes()];
      data.getBytes(0, responsePacket);
      this.responsePacketOpt = Optional.of(responsePacket);
      final var remoteEphemeralKey =
          ECPublicKey.fromBytes(message.getEphemeralPublicKey().asBytes());
      return finalizeHandshake(remoteEphemeralKey, message.getNonce(), message.getNewestForkName());
    } catch (PublicKeyException | InvalidCipherTextException ex) {
      return AuthHandshakeResult.error(
          String.format("Handshake decryption failed (%s)", ex.getMessage()), Optional.empty());
    }
  }

  private ECPublicKey extractEphemeralKey(
      ECDSASignature signature, HashCode nonce, ECPublicKey publicKey) {
    final var sharedSecret = ecKeyOps.ecdhAgreement(publicKey);
    final var token = bigIntegerToBytes(sharedSecret, NONCE_SIZE);
    final var signed = xor(token, nonce.asBytes());
    return ECPublicKey.recoverFrom(HashCode.fromBytes(signed), signature).orElseThrow();
  }

  private AuthHandshakeSuccess finalizeHandshake(
      ECPublicKey remoteEphemeralKey, HashCode remoteNonce, Optional<String> remoteNewestForkName) {
    final var initiatePacket = initiatePacketOpt.get();
    final var responsePacket = responsePacketOpt.get();
    final var remotePubKey = remotePubKeyOpt.get();

    final var agreement = new ECDHBasicAgreement();
    agreement.init(
        new ECPrivateKeyParameters(
            new BigInteger(1, ephemeralKey.getPrivateKey()), ECKeyUtils.domain()));
    final var secretScalar =
        agreement.calculateAgreement(
            new ECPublicKeyParameters(remoteEphemeralKey.getEcPoint(), ECKeyUtils.domain()));
    final var agreedSecret = bigIntegerToBytes(secretScalar, SECRET_SIZE);

    final var sharedSecret =
        isInitiator
            ? kec256(agreedSecret, kec256(remoteNonce.asBytes(), nonce))
            : kec256(agreedSecret, kec256(nonce, remoteNonce.asBytes()));

    final var aesSecret = kec256(agreedSecret, sharedSecret);

    final var macSecrets =
        isInitiator
            ? macSecretSetup(
                agreedSecret,
                aesSecret,
                initiatePacket,
                nonce,
                responsePacket,
                remoteNonce.asBytes())
            : macSecretSetup(
                agreedSecret,
                aesSecret,
                initiatePacket,
                remoteNonce.asBytes(),
                responsePacket,
                nonce);

    final var secrets =
        new Secrets(
            aesSecret,
            kec256(agreedSecret, aesSecret),
            kec256(sharedSecret),
            macSecrets.getFirst(),
            macSecrets.getSecond());

    return AuthHandshakeResult.success(remotePubKey, secrets, remoteNewestForkName);
  }

  private Pair<KeccakDigest, KeccakDigest> macSecretSetup(
      byte[] agreedSecret,
      byte[] aesSecret,
      byte[] initiatePacket,
      byte[] initiateNonce,
      byte[] responsePacket,
      byte[] responseNonce) {
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
