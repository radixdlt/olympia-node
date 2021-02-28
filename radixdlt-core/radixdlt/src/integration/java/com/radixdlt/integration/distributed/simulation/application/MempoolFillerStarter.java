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

import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;

/**
 * Starts a mempool filler
 */
public final class MempoolFillerStarter implements SimulationTest.SimulationNetworkActor {
    @Override
    public void start(SimulationNodes.RunningNetwork network) {
        EventDispatcher<MempoolFillerUpdate> dispatcher = network
                .getDispatcher(MempoolFillerUpdate.class, network.getNodes().get(0));
        dispatcher.dispatch(MempoolFillerUpdate.create(true));
    }

    @Override
    public void stop() {
    }
}
