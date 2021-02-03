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

package com.radixdlt.chaos;

import com.radixdlt.consensus.bft.BFTNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Event specifying the node to message flood
 */
public final class MessageFloodUpdate {
    private final BFTNode bftNode;

    private MessageFloodUpdate(BFTNode bftNode) {
        this.bftNode = bftNode;
    }

    public static MessageFloodUpdate create(BFTNode bftNode) {
        Objects.requireNonNull(bftNode);
        return new MessageFloodUpdate(bftNode);
    }

    public static MessageFloodUpdate disable() {
        return new MessageFloodUpdate(null);
    }

    public Optional<BFTNode> getBFTNode() {
        return Optional.ofNullable(bftNode);
    }
}
