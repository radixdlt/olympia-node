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

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ConsensusHasher {
	private ConsensusHasher() {
		throw new IllegalStateException();
	}

	public static HashCode toHash(HashCode opaque, LedgerHeader header, long nodeTimestamp, Hasher hasher) {
		var raw = new ByteArrayOutputStream();
		var outputStream = new DataOutputStream(raw);
		try {
			outputStream.writeInt(header != null ? 0 : 1); // 4 bytes (Version)
			outputStream.write(opaque.asBytes()); // 32 bytes
			if (header != null) {
				outputStream.write(header.getAccumulatorState().getAccumulatorHash().asBytes()); // 32 bytes
				outputStream.writeLong(header.getAccumulatorState().getStateVersion()); // 8 bytes
				outputStream.writeLong(header.getEpoch()); // 8 bytes
				outputStream.writeLong(header.getView().number()); // 8 bytes
				outputStream.writeLong(header.timestamp()); // 8 bytes
				if (header.getNextValidatorSet().isPresent()) {
					var vset = header.getNextValidatorSet().get();
					outputStream.writeInt(vset.getValidators().size()); // 4 bytes
					for (var v : vset.getValidators().asList()) {
						var key = v.getNode().getKey().getCompressedBytes();
						outputStream.write(key);
						var power = v.getPower();
						outputStream.write(power.toByteArray());
					}
				} else {
					outputStream.writeInt(0); // 4 bytes
				}
			}
			outputStream.writeLong(nodeTimestamp); // 8 bytes
		} catch (IOException e) {
			throw new IllegalStateException();
		}
		var toHash = raw.toByteArray();
		return hasher.hashBytes(toHash);
	}
}