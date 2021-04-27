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

import org.bouncycastle.crypto.digests.KeccakDigest;

/**
 * Secrets agreed upon during the auth handshake. Used for encrypted communication (FrameCodec).
 */
public final class Secrets {
	final byte[] aes;
	final byte[] mac;
	final byte[] token;
	final KeccakDigest egressMac;
	final KeccakDigest ingressMac;

	public Secrets(byte[] aes, byte[] mac, byte[] token, KeccakDigest egressMac, KeccakDigest ingressMac) {
		this.aes = aes;
		this.mac = mac;
		this.token = token;
		this.egressMac = egressMac;
		this.ingressMac = ingressMac;
	}

	public byte[] getAes() {
		return aes;
	}

	public byte[] getMac() {
		return mac;
	}

	public byte[] getToken() {
		return token;
	}

	public KeccakDigest getEgressMac() {
		return egressMac;
	}

	public KeccakDigest getIngressMac() {
		return ingressMac;
	}
}
