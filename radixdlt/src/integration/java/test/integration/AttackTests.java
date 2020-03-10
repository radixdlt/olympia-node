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

package test.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AttackTests {
//	@Test
	public void hashGeneratorEstimator() {
		final int maxLeading = 40;

		Random random = new Random();
		byte[] candidate = new byte[32];

		for (int l = 0; l < maxLeading; l++) {
			long start = System.currentTimeMillis();
			boolean found = false;
			long iterations = 0;

			while (!found) {
				random.nextBytes(candidate);

				BitSet bitset = BitSet.valueOf(candidate);

				if (bitset.nextClearBit(0) == l) {
					System.out.println(
							l + ": " + iterations + " iterations  Took: " + (System.currentTimeMillis() - start));
					found = true;
				} else {
					iterations++;
				}
			}
		}
	}

//	@Test
	public void gossipDiscoveryTablePoisoning() {
		int matchRange = 4;

		Random random = new Random();
		Map<Integer, ArrayList<byte[]>> gossipMap = new HashMap<Integer, ArrayList<byte[]>>();

		byte[] target = new byte[32];
		random.nextBytes(target);
		target[0] = 0;
//		target[1] = 0;

		long iterations = 0;
		int discovered = 0;
		byte[] candidate = new byte[32];

		while (discovered < 1000) {
			random.nextBytes(candidate);

			iterations++;

			if (candidate[0] != 0) {
				continue;
			}

//			if (candidate[0] != 0 || candidate[1] != 0)
//				continue;

			int slot = getGossipMapSlot(target, candidate);

			if (!gossipMap.containsKey(slot)) {
				gossipMap.put(slot, new ArrayList<byte[]>());
			}

			gossipMap.get(slot).add(candidate);

			discovered++;
		}

		int maxSlot = 0;
		for (int i = 0; i < target.length; i++) {
			if (gossipMap.containsKey(i)) {
				maxSlot = i;
				System.out.println("Slot " + i + ": " + gossipMap.get(i).size() + " items");
			}
		}

		byte[] result = new byte[32];
		boolean found = false;
		iterations = 0;
		while (!found) {
			random.nextBytes(candidate);
//			candidate[0] = 0;
//			candidate[1] = 0;

			if (getGossipMapSlot(target, candidate) > maxSlot) {
				found = false;
				break;
			}

			iterations++;
		}

		System.out.println("Target: " + Arrays.toString(target));
		System.out.println("Candidate: " + Arrays.toString(candidate));
		System.out.println("Result: " + Arrays.toString(result));
		System.out.println("Iterations: " + iterations);
	}

	public int getGossipMapSlot(byte[] target, byte[] candidate) {
		BitSet targetBitSet = BitSet.valueOf(target);
		BitSet candidateBitSet = BitSet.valueOf(candidate);

		candidateBitSet.xor(targetBitSet);
		return candidateBitSet.nextSetBit(0);

/*		byte[] result = new byte[32];

		for (int i = 0 ; i < candidate.length ; i++) {
			result[i] = (byte) (candidate[i] ^ target[i]);
		}

		for (int i = 0 ; i < result.length ; i++) {
			if (result[i] != 0) {
				return i;
			}
		}

		return result.length;*/
	}
}
