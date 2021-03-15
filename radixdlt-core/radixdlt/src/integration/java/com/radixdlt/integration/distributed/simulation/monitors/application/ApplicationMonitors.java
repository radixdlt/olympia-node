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

package com.radixdlt.integration.distributed.simulation.monitors.application;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.radixdlt.integration.distributed.simulation.Monitor;
import com.radixdlt.integration.distributed.simulation.MonitorKey;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.application.LocalMempoolPeriodicSubmitter;
import com.radixdlt.integration.distributed.simulation.application.NodeValidatorRegistrator;
import com.radixdlt.integration.distributed.simulation.monitors.NodeEvents;
import com.radixdlt.utils.Pair;

/**
 * Monitors which occur at the mempool level or higher
 */
public final class ApplicationMonitors {
    private ApplicationMonitors() {
        throw new IllegalStateException("Cannot instantiate.");
    }

    public static Module registeredNodeToEpoch() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.VALIDATOR_REGISTERED)
            TestInvariant registeredValidator(NodeValidatorRegistrator validatorRegistrator) {
                return new RegisteredValidatorChecker(validatorRegistrator.validatorRegistrationSubmissions());
            }
        };
    }

    public static Module mempoolCommitted() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.MEMPOOL_COMMITTED)
            TestInvariant mempoolCommitted(LocalMempoolPeriodicSubmitter mempoolSubmitter, NodeEvents nodeEvents) {
                return new CommittedChecker(mempoolSubmitter.issuedCommands().map(Pair::getFirst), nodeEvents);
            }
        };
    }
}
