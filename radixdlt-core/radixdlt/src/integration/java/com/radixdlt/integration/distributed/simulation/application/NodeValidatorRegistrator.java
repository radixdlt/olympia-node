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

package com.radixdlt.integration.distributed.simulation.application;

import com.radixdlt.application.validator.ValidatorRegistration;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Registers nodes in order as validators
 */
public final class NodeValidatorRegistrator implements SimulationTest.SimulationNetworkActor {
    private Disposable disposable;
    private Subject<BFTNode> validationRegistrations = BehaviorSubject.create();

    @Override
    public void start(SimulationNodes.RunningNetwork network) {
        List<BFTNode> nodes = network.getNodes();
        this.disposable = Observable.fromIterable(nodes)
            .concatMap(i -> Observable.timer(3, TimeUnit.SECONDS).map(l -> i))
            .doOnNext(validationRegistrations::onNext)
            .map(node -> network.getDispatcher(ValidatorRegistration.class, node))
            .subscribe(d -> d.dispatch(ValidatorRegistration.create(true)));
    }

    @Override
    public void stop() {
        this.disposable.dispose();
    }

    public Observable<BFTNode> validatorRegistrationSubmissions() {
        return validationRegistrations;
    }
}
