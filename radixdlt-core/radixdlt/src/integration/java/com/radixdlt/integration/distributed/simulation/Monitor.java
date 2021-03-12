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
 */

package com.radixdlt.integration.distributed.simulation;

/**
 * Keys for different monitor checks
 */
public enum Monitor {
    CONSENSUS_SAFETY,
    CONSENSUS_LIVENESS,
    CONSENSUS_NO_TIMEOUTS,
    CONSENSUS_NO_EPOCH_TIMEOUTS,
    CONSENSUS_DIRECT_PARENTS,
    CONSENSUS_NONE_COMMITTED,
    CONSENSUS_TO_LEDGER_PROCESSED,
    CONSENSUS_TIMESTAMP_CHECK,
    CONSENSUS_VERTEX_REQUEST_RATE,
    EPOCH_CEILING_VIEW,
    LEDGER_IN_ORDER,
    MEMPOOL_COMMITTED,
    VALIDATOR_REGISTERED,
    RADIX_ENGINE_NO_INVALID_PROPOSED_COMMANDS
}
