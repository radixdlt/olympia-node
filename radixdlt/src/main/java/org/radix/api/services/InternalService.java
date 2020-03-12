/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.api.services;

import com.radixdlt.common.Atom;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

import java.util.Random;
import org.json.JSONObject;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRI;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.submission.SubmissionControl;
import com.radixdlt.crypto.CryptoException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.Time;

/**
 * API which is used for internal testing and should not be included in release to users
 */
public final class InternalService {
	private static final Logger log = Logging.getLogger();
	private final SubmissionControl submissionControl;
	private final RuntimeProperties properties;
	private final Universe universe;

	private static boolean spamming = false;

	public InternalService(SubmissionControl submissionControl, RuntimeProperties properties, Universe universe) {
		this.submissionControl = submissionControl;
		this.properties = properties;
		this.universe = universe;
	}

	private class Spammer implements Runnable {
		private final int nonceBits;

		private final ECKeyPair owner;
		private final RadixAddress account;
		private final int     iterations;
		private final int     batching;
		private final int     rate;

		// This is safe, as it is just used to generate random nonces for testing
		private final Random random = new Random();

		public Spammer(ECKeyPair owner, int iterations, int batching, int rate, int nonceBits) {
			this.owner = owner;
			this.iterations = iterations;
			this.rate = rate;
			this.batching = Math.max(1, batching);
			this.account = RadixAddress.from(universe, owner.getPublicKey());
			this.nonceBits = nonceBits;
		}

		private byte[] generateNonce(int randomBits) {
			if (randomBits > 0) {
				int byteCount = (randomBits + Byte.SIZE - 1) / Byte.SIZE;
				byte[] bytes = new byte[byteCount];
				synchronized (random) {
					random.nextBytes(bytes);
				}
				int extraBits = (byteCount * Byte.SIZE) - randomBits;
				if (extraBits != 0) {
					byte mask = (byte) ((1 << (Byte.SIZE - extraBits)) - 1);
					bytes[0] &= mask;
				}
				return bytes;
			}
			return Bytes.EMPTY_BYTES;
		}

		private long getRandomLong() {
			synchronized (random) {
				return random.nextLong();
			}
		}

		@Override
		public void run() {
			try {
				spamming = true;

				int remainingIterations = this.iterations;

				while (true) {
					try {
						long sliceStart = System.currentTimeMillis();

						for (int i = 0; i < this.rate; i++) {
							Atom atom = new Atom(Time.currentTimestamp(), ImmutableMap.of("magic", "0xdeadbeef"));

							for (int b = 0; b < this.batching; b++) {
								byte[] nonce = generateNonce(nonceBits);
								String rriName = nonce != null ? Bytes.toHexString(nonce) : "hi";
								RRI rri = RRI.of(this.account, rriName);
								RRIParticle rriParticle = new RRIParticle(rri);
								UniqueParticle unique = new UniqueParticle(rriName, this.account, getRandomLong());
								atom.addParticleGroupWith(rriParticle, Spin.DOWN, unique, Spin.UP);
							}

							atom.sign(this.owner);

							submissionControl.submitAtom(atom);

							remainingIterations--;
							if (remainingIterations <= 0) {
								return;
							}

							if (i % this.rate == this.rate - 1) {
								if (1000 - (System.currentTimeMillis() - sliceStart) > 0) {
									Thread.sleep(1000 - (System.currentTimeMillis() - sliceStart));
								}

								sliceStart = System.currentTimeMillis();
							}
						}
					} catch (Exception ex) {
						log.error(ex);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException nil) {
							// Exit if we are interrupted
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			} finally {
				spamming = false;
			}
		}
	}

	public JSONObject spamathon(JSONObject params) {
		return this.spamathon(params.getString("iterations"), params.optString("batching"), params.getString("rate"));
	}

	public JSONObject spamathon(String iterations, String batching, String rate) {
		JSONObject result = new JSONObject();

		if (spamming && this.properties.get("atoms.spam.multiple", false)) {
			throw new RuntimeException();
		}
		if (iterations == null) {
			throw new RuntimeException("Iterations not supplied");
		}
		if (Integer.decode(iterations) < 1) {
			throw new RuntimeException("Iterations is invalid");
		}
		if (rate == null) {
			throw new RuntimeException("Rate not supplied");
		}
		if (Integer.decode(rate) < 1) {
			throw new RuntimeException("Rate is invalid");
		}
		if (Integer.decode(rate) > this.properties.get("atoms.spam.max_rate", 1000)) {
			Integer maxRate = this.properties.get("atoms.spam.max_rate", 1000);
			throw new RuntimeException("Rate is too high - Maximum rate is " + maxRate);
		}

		try {
			int nonceBits = this.properties.get("test.nullatom.junk_size", 40);
			Thread spammerThread = new Thread(new Spammer(new ECKeyPair(), Integer.decode(iterations), batching == null ? 1 : Integer.decode(batching), Integer.decode(rate), nonceBits));
			spammerThread.setDaemon(true);
			spammerThread.setName("Spammer " + System.currentTimeMillis());
			spammerThread.start();

			result.put("data", "OK");
		} catch (CryptoException e) {
			result.put("error", e.getMessage());
		}

		return result;
	}
}
