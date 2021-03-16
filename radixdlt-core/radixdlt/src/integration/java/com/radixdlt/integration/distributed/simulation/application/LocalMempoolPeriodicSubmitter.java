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

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.SimulationTest.SimulationNetworkActor;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.TimeUnit;

/**
 * Contributes to steady state by submitting commands to the mempool every few seconds
 */
public class LocalMempoolPeriodicSubmitter implements SimulationNetworkActor {

	private final PublishSubject<Pair<Command, BFTNode>> commands;
	private final CommandGenerator commandGenerator;
	private final NodeSelector nodeSelector;

	private Disposable commandsDisposable;

	public LocalMempoolPeriodicSubmitter(CommandGenerator commandGenerator, NodeSelector nodeSelector) {
		this.commands = PublishSubject.create();
		this.commandGenerator = commandGenerator;
		this.nodeSelector = nodeSelector;
	}

	private void act(RunningNetwork network, Command command, BFTNode node) {
		network.getDispatcher(MempoolAdd.class, node).dispatch(MempoolAdd.create(command));
	}

	public Observable<Pair<Command, BFTNode>> issuedCommands() {
		return commands.observeOn(Schedulers.io());
	}

	@Override
	public void start(RunningNetwork network) {
		if (commandsDisposable != null) {
			return;
		}

		commandsDisposable = Observable.interval(1, 10, TimeUnit.SECONDS)
			.map(i -> commandGenerator.nextCommand())
			.flatMapSingle(cmd -> nodeSelector.nextNode(network).map(node -> Pair.of(cmd, node)))
			.doOnNext(p -> this.act(network, p.getFirst(), p.getSecond()))
			.subscribe(commands::onNext);
	}

	@Override
	public void stop() {
		commandsDisposable.dispose();
		commands.onComplete();
	}
}
