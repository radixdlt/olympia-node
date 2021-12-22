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

package com.radixdlt.network.p2p.transport;

import com.radixdlt.network.p2p.transport.handshake.Secrets;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/** Low-level codec for encrypted communication. */
public final class FrameCodec {
  private static final int HEADER_SIZE = 32;
  private static final int MAC_SIZE = 16;

  private final StreamCipher enc;
  private final StreamCipher dec;
  private final KeccakDigest egressMac;
  private final KeccakDigest ingressMac;
  private final byte[] mac;

  public FrameCodec(Secrets secrets) {
    this.mac = secrets.getMac();

    final var encCipher = new AESEngine();
    enc = new SICBlockCipher(encCipher);
    enc.init(
        true,
        new ParametersWithIV(
            new KeyParameter(secrets.getAes()), new byte[encCipher.getBlockSize()]));

    final var decCipher = new AESEngine();
    dec = new SICBlockCipher(decCipher);
    dec.init(
        false,
        new ParametersWithIV(
            new KeyParameter(secrets.getAes()), new byte[decCipher.getBlockSize()]));

    egressMac = secrets.getEgressMac();
    ingressMac = secrets.getIngressMac();
  }

  public void writeFrame(byte[] frame, OutputStream out) throws IOException {
    final var headBuffer = new byte[32];
    headBuffer[0] = (byte) (frame.length >> 16);
    headBuffer[1] = (byte) (frame.length >> 8);
    headBuffer[2] = (byte) (frame.length);

    enc.processBytes(headBuffer, 0, 16, headBuffer, 0);
    final var headBufferMacResult = updateEgressMac(egressMac, headBuffer);
    out.write(headBuffer, 0, 16);
    out.write(headBufferMacResult, 0, 16);

    final var buff = new byte[256];
    final var payloadStream = new ByteArrayInputStream(frame);
    while (true) {
      final var n = payloadStream.read(buff);
      if (n <= 0) {
        break;
      }
      enc.processBytes(buff, 0, n, buff, 0);
      egressMac.update(buff, 0, n);
      out.write(buff, 0, n);
    }
    final var paddingSize = 16 - (frame.length % 16);
    final var padding = new byte[16];
    if (paddingSize < 16) {
      enc.processBytes(padding, 0, paddingSize, buff, 0);
      egressMac.update(buff, 0, paddingSize);
      out.write(buff, 0, paddingSize);
    }

    final var macBuffer = new byte[egressMac.getDigestSize()];
    doSum(egressMac, macBuffer);
    final var egressMacResult = updateEgressMac(egressMac, macBuffer);
    out.write(egressMacResult, 0, 16);
  }

  public Optional<byte[]> tryReadSingleFrame(ByteBuf input) throws IOException {
    if (input.readableBytes() < HEADER_SIZE) {
      return Optional.empty();
    }

    final var totalBodySize = readHeader(input);

    if (input.readableBytes() < HEADER_SIZE + totalBodySize) {
      return Optional.empty();
    }

    final var paddingSize = totalBodySize % 16 == 0 ? 0 : 16 - (totalBodySize % 16);
    final var payloadBuffer = new byte[totalBodySize + paddingSize + MAC_SIZE];
    input.getBytes(HEADER_SIZE, payloadBuffer, 0, payloadBuffer.length);

    final var frameSize = payloadBuffer.length - MAC_SIZE;
    ingressMac.update(payloadBuffer, 0, frameSize);
    dec.processBytes(payloadBuffer, 0, frameSize, payloadBuffer, 0);

    final var macBuffer = new byte[ingressMac.getDigestSize()];
    doSum(ingressMac, macBuffer);
    updateIngressMac(ingressMac, macBuffer, payloadBuffer, frameSize);

    final var bodyBuffer = new byte[totalBodySize];
    System.arraycopy(payloadBuffer, 0, bodyBuffer, 0, totalBodySize);

    return Optional.of(bodyBuffer);
  }

  private int readHeader(ByteBuf input) throws IOException {
    final var headBuffer = new byte[32];
    input.getBytes(0, headBuffer, 0, headBuffer.length);

    updateIngressMac(ingressMac, headBuffer, headBuffer, 16);
    dec.processBytes(headBuffer, 0, 16, headBuffer, 0);

    int totalBodySize = headBuffer[0] & 0xFF;
    totalBodySize = (totalBodySize << 8) + (headBuffer[1] & 0xFF);
    totalBodySize = (totalBodySize << 8) + (headBuffer[2] & 0xFF);

    return totalBodySize;
  }

  private void updateIngressMac(KeccakDigest mac, byte[] seed, byte[] out, int outOffset)
      throws IOException {
    final var result = updateMac(mac, seed);

    for (int i = 0; i < MAC_SIZE; i++) {
      if (out[i + outOffset] != result[i]) {
        throw new IOException("MAC mismatch");
      }
    }
  }

  private byte[] updateEgressMac(KeccakDigest mac, byte[] seed) {
    return updateMac(mac, seed);
  }

  private byte[] updateMac(KeccakDigest mac, byte[] seed) {
    final var aesBlock = new byte[mac.getDigestSize()];
    doSum(mac, aesBlock);
    makeMacCipher().processBlock(aesBlock, 0, aesBlock, 0);
    for (int i = 0; i < MAC_SIZE; i++) {
      aesBlock[i] ^= seed[i];
    }
    mac.update(aesBlock, 0, MAC_SIZE);
    final var result = new byte[mac.getDigestSize()];
    doSum(mac, result);
    return result;
  }

  private AESEngine makeMacCipher() {
    final var aesEngine = new AESEngine();
    aesEngine.init(true, new KeyParameter(mac));
    return aesEngine;
  }

  private void doSum(KeccakDigest mac, byte[] out) {
    new KeccakDigest(mac).doFinal(out, 0);
  }
}
