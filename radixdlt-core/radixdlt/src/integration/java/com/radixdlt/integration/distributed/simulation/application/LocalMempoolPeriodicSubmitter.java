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

import com.radixdlt.atom.Txn;
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

	private final PublishSubject<Pair<Txn, BFTNode>> txns;
	private final TxnGenerator txnGenerator;
	private final NodeSelector nodeSelector;

	private Disposable commandsDisposable;

	public LocalMempoolPeriodicSubmitter(TxnGenerator txnGenerator, NodeSelector nodeSelector) {
		this.txns = PublishSubject.create();
		this.txnGenerator = txnGenerator;
		this.nodeSelector = nodeSelector;
	}

	private void act(RunningNetwork network, Txn txn, BFTNode node) {
		network.getDispatcher(MempoolAdd.class, node).dispatch(MempoolAdd.create(txn));
	}

	public Observable<Pair<Txn, BFTNode>> issuedTxns() {
		return txns.observeOn(Schedulers.io());
	}

	@Override
	public void start(RunningNetwork network) {
		if (commandsDisposable != null) {
			return;
		}

		commandsDisposable = Observable.interval(1, 10, TimeUnit.SECONDS)
			.map(i -> txnGenerator.nextTxn())
			.flatMapSingle(cmd -> nodeSelector.nextNode(network).map(node -> Pair.of(cmd, node)))
			.doOnNext(p -> this.act(network, p.getFirst(), p.getSecond()))
			.subscribe(txns::onNext);
	}

	@Override
	public void stop() {
		commandsDisposable.dispose();
		txns.onComplete();
	}
}
