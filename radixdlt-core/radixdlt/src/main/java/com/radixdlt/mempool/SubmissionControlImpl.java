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

package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.DeserializeException;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

public class SubmissionControlImpl implements SubmissionControl {
	private static final Logger log = LogManager.getLogger();

	private final Mempool mempool;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final Serialization serialization;
	private final EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher;

	public SubmissionControlImpl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.serialization = Objects.requireNonNull(serialization);

		this.mempoolAddFailureEventDispatcher = Objects.requireNonNull(mempoolAddFailureEventDispatcher);
	}

	@Override
	public void submitCommand(Command command) {
		ClientAtom clientAtom = command.map(payload -> {
			try {
				return serialization.fromDson(payload, ClientAtom.class);
			} catch (DeserializeException e) {
				return null;
			}
		});
		if (clientAtom == null) {
			mempoolAddFailureEventDispatcher.dispatch(MempoolAddFailure.create(
				command,
				new MempoolRejectedException(command, "Bad atom")
			));
			return;
		}

		try {
			this.radixEngine.staticCheck(clientAtom);
			this.mempool.add(command);
		} catch (RadixEngineException | MempoolFullException | MempoolDuplicateException e) {
			log.info(
				"Rejecting atom {} with error '{}'.",
				clientAtom,
				e.getMessage()
			);

			mempoolAddFailureEventDispatcher.dispatch(MempoolAddFailure.create(command, e));
		}
	}

	@Override
	public String toString() {
		return String.format("%s[%x]", getClass().getSimpleName(), System.identityHashCode(this));
	}
}
