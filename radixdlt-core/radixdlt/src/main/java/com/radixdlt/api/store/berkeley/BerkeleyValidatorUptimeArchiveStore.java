/* Copyright 2021 Radix DLT Ltd incorporated in England.
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
