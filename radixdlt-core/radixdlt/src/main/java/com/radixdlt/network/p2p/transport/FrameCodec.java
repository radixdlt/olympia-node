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

package com.radixdlt.network.p2p.transport;

import com.radixdlt.network.p2p.transport.handshake.Secrets;
import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Low-level codec for encrypted communication.
 */
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
		enc.init(true, new ParametersWithIV(new KeyParameter(secrets.getAes()), new byte[encCipher.getBlockSize()]));

		final var decCipher = new AESEngine();
		dec = new SICBlockCipher(decCipher);
		dec.init(false, new ParametersWithIV(new KeyParameter(secrets.getAes()), new byte[decCipher.getBlockSize()]));

		egressMac = secrets.getEgressMac();
		ingressMac = secrets.getIngressMac();
	}

	public void writeFrame(byte[] frame, OutputStream out) throws IOException {
		final var headBuffer = new byte[32];
		headBuffer[0] = (byte) (frame.length >> 16);
		headBuffer[1] = (byte) (frame.length >> 8);
		headBuffer[2] = (byte) (frame.length);

		enc.processBytes(headBuffer, 0, 16, headBuffer, 0);
		updateMac(egressMac, headBuffer, headBuffer, 16, true);

		final var buff = new byte[256];
		out.write(headBuffer);
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
		updateMac(egressMac, macBuffer, macBuffer, 0, true);
		out.write(macBuffer, 0, 16);
	}

	public Optional<byte[]> tryReadSingleFrame(byte[] input) throws IOException {
		if (input.length < HEADER_SIZE) {
			return Optional.empty();
		}

		final var totalBodySize = readHeader(input);

		if (input.length < HEADER_SIZE + totalBodySize) {
			return Optional.empty();
		}

		final var paddingSize = totalBodySize % 16 == 0 ? 0 : 16 - (totalBodySize % 16);
		final var payloadBuffer = new byte[totalBodySize + paddingSize + MAC_SIZE];
		System.arraycopy(input, HEADER_SIZE, payloadBuffer, 0, payloadBuffer.length);

		final var frameSize = payloadBuffer.length - MAC_SIZE;
		ingressMac.update(payloadBuffer, 0, frameSize);
		dec.processBytes(payloadBuffer, 0, frameSize, payloadBuffer, 0);

		final var macBuffer = new byte[ingressMac.getDigestSize()];
		doSum(ingressMac, macBuffer);
		updateMac(ingressMac, macBuffer, payloadBuffer, frameSize, false);

		final var bodyBuffer = new byte[totalBodySize];
		System.arraycopy(payloadBuffer, 0, bodyBuffer, 0, totalBodySize);

		return Optional.of(bodyBuffer);
	}

	private int readHeader(byte[] input) throws IOException {
		final var headBuffer = new byte[32];
		System.arraycopy(input, 0, headBuffer, 0, 32);

		updateMac(ingressMac, headBuffer, headBuffer, 16, false);
		dec.processBytes(headBuffer, 0, 16, headBuffer, 0);

		int totalBodySize = headBuffer[0] & 0xFF;
		totalBodySize = (totalBodySize << 8) + (headBuffer[1] & 0xFF);
		totalBodySize = (totalBodySize << 8) + (headBuffer[2] & 0xFF);

		return totalBodySize;
	}

	private void updateMac(KeccakDigest mac, byte[] seed, byte[] out, int outOffset, boolean egress) throws IOException {
		final var aesBlock = new byte[mac.getDigestSize()];
		doSum(mac, aesBlock);
		makeMacCipher().processBlock(aesBlock, 0, aesBlock, 0);
		for (int i = 0; i < MAC_SIZE; i++) {
			aesBlock[i] ^= seed[i];
		}
		mac.update(aesBlock, 0, MAC_SIZE);
		final var result = new byte[mac.getDigestSize()];
		doSum(mac, result);

		if (egress) {
			System.arraycopy(result, 0, out, outOffset, MAC_SIZE);
		} else {
			for (int i = 0; i < MAC_SIZE; i++) {
				if (out[i + outOffset] != result[i]) {
					throw new IOException("MAC mismatch");
				}
			}
		}
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
