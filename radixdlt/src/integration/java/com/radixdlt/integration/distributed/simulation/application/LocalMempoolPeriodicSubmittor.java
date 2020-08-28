/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.integration.distributed.simulation.application;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Contributes to steady state by submitting commands to the mempool every few seconds
 */
public abstract class LocalMempoolPeriodicSubmittor {
	private final PublishSubject<Pair<Command, BFTNode>> commands;
	private final Random random = new Random();

	public LocalMempoolPeriodicSubmittor() {
		this.commands = PublishSubject.create();
	}

	abstract Command nextCommand();

	private Pair<Command, BFTNode> act(RunningNetwork network, EpochChange lastEpochChange) {
		ImmutableList<BFTValidator> validators = lastEpochChange.getValidatorSet().getValidators().asList();
		int validatorSetSize = validators.size();
		BFTValidator validator = validators.get(random.nextInt(validatorSetSize));
		BFTNode node = validator.getNode();

		Mempool mempool = network.getMempool(node);
		Command command = nextCommand();
		try {
			mempool.add(command);
		} catch (MempoolDuplicateException | MempoolFullException e) {
			// TODO: Cleanup
			e.printStackTrace();
		}

		return Pair.of(command, node);
	}

	public Observable<Pair<Command, BFTNode>> issuedCommands() {
		return commands;
	}

	public void run(RunningNetwork network) {
		Observable.interval(1, 10, TimeUnit.SECONDS)
			.withLatestFrom(network.latestEpochChanges(), (t, e) -> e)
			.map(e -> this.act(network, e))
			.subscribe(commands);
	}
}
