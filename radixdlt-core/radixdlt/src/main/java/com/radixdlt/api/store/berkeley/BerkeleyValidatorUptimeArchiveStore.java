/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api.store.berkeley;

import com.radixdlt.api.store.ValidatorUptime;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;

/**
 * Keeps historical information on uptime of each validator
 */
public final class BerkeleyValidatorUptimeArchiveStore implements BerkeleyAdditionalStore {
	private static final String VALIDATOR_UPTME_DB = "radix.validator_uptime_db";
	private Database validatorUptime;
	private final AtomicLong curEpoch = new AtomicLong();
	private static final long EPOCH_WINDOW_LENGTH = 150;

	private void downEpoch(Transaction dbTxn, long epoch) {
		var nextEpoch = epoch + 1;
		var key = new DatabaseEntry(new byte[0]);
		var value = new DatabaseEntry(Longs.toByteArray(nextEpoch));
		validatorUptime.put(dbTxn, key, value);
		curEpoch.set(nextEpoch);
	}

	private void store(Transaction dbTxn, ValidatorBFTData validatorBFTData) {
		var buf = ByteBuffer.allocate(Long.BYTES + ECPublicKey.COMPRESSED_BYTES);
		var epoch = curEpoch.get();
		buf.putLong(epoch);
		buf.put(validatorBFTData.getValidatorKey().getCompressedBytes());

		var valueBuf = ByteBuffer.allocate(Long.BYTES + Long.BYTES);
		valueBuf.putLong(validatorBFTData.proposalsCompleted());
		valueBuf.putLong(validatorBFTData.proposalsMissed());

		var key = new DatabaseEntry(buf.array());
		var value = new DatabaseEntry(valueBuf.array());
		validatorUptime.put(dbTxn, key, value);
	}

	public Map<ECPublicKey, ValidatorUptime> getUptimeTwoWeeks() {
		var map = new HashMap<ECPublicKey, ValidatorUptime>();
		long epochStart = Math.max(curEpoch.get() - EPOCH_WINDOW_LENGTH, 0);
		try (var cursor = validatorUptime.openCursor(null, null)) {
			var key = new DatabaseEntry(Longs.toByteArray(epochStart));
			var value = new DatabaseEntry();
			var status = cursor.getSearchKeyRange(key, value, null);
			while (status == OperationStatus.SUCCESS && value.getData().length > 0) {
				var keyBuf = ByteBuffer.wrap(key.getData());
				var epoch = keyBuf.getLong();
				var pubKeyBytes = new byte[ECPublicKey.COMPRESSED_BYTES];
				keyBuf.get(pubKeyBytes);
				ECPublicKey publicKey;
				try {
					publicKey = ECPublicKey.fromBytes(pubKeyBytes);
				} catch (PublicKeyException e) {
					throw new IllegalStateException();
				}

				var buf = ByteBuffer.wrap(value.getData());
				var proposalsCompleted = buf.getLong();
				var proposalsMissed = buf.getLong();
				var uptime = ValidatorUptime.create(proposalsCompleted, proposalsMissed);
				map.merge(publicKey, uptime, ValidatorUptime::merge);
				status = cursor.getNext(key, value, null);
			}

			return map;
		}
	}

	@Override
	public void open(DatabaseEnvironment dbEnv) {
		var env = dbEnv.getEnvironment();
		validatorUptime = env.openDatabase(null, VALIDATOR_UPTME_DB, new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
		var curEpochEntry = new DatabaseEntry();
		var result = validatorUptime.get(null, new DatabaseEntry(new byte[0]), curEpochEntry, null);
		if (result == OperationStatus.SUCCESS) {
			this.curEpoch.set(Longs.fromByteArray(curEpochEntry.getData()));
		}
	}

	@Override
	public void close() {
		if (validatorUptime != null) {
			validatorUptime.close();
		}
	}

	@Override
	public void process(Transaction dbTxn, REProcessedTxn txn) {
		for (var groupedUpdates : txn.getGroupedStateUpdates()) {
			for (var update : groupedUpdates) {
				if (update.isShutDown() && update.getParsed() instanceof EpochData) {
					var epochData = (EpochData) update.getParsed();
					downEpoch(dbTxn, epochData.getEpoch());
				}

				if (update.isBootUp() && update.getParsed() instanceof ValidatorBFTData) {
					var validatorBftData = (ValidatorBFTData) update.getParsed();
					store(dbTxn, validatorBftData);
				}
			}
		}
	}
}
