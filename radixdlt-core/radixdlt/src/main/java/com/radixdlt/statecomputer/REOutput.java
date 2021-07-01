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

package com.radixdlt.statecomputer;

import com.radixdlt.constraintmachine.REProcessedTxn;

import java.util.List;
import java.util.Objects;

/**
 * Event signifying that an atom was committed to ledger successfully
 */
public final class REOutput {
    private final List<REProcessedTxn> processed;

    private REOutput(List<REProcessedTxn> processed) {
        this.processed = processed;
    }

    public static REOutput create(List<REProcessedTxn> processed) {
        Objects.requireNonNull(processed);
        return new REOutput(processed);
    }

    public List<REProcessedTxn> getProcessedTxns() {
        return processed;
    }
}
