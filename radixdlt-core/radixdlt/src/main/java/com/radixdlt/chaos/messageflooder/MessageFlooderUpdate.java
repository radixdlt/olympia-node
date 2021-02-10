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

package com.radixdlt.chaos.messageflooder;

import com.radixdlt.consensus.bft.BFTNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Event specifying the node to message flood
 */
public final class MessageFlooderUpdate {
    private final BFTNode bftNode;
    private final int messagesPerSec;
    private final int commandSize;

    private MessageFlooderUpdate(BFTNode bftNode, int messagesPerSec, int commandSize) {
        this.bftNode = bftNode;
        this.messagesPerSec = messagesPerSec;
        this.commandSize = commandSize;
    }

    public static MessageFlooderUpdate create() {
        return new MessageFlooderUpdate(null, -1, -1);
    }

    public MessageFlooderUpdate bftNode(BFTNode bftNode) {
        return new MessageFlooderUpdate(bftNode, messagesPerSec, commandSize);
    }

    public MessageFlooderUpdate messagesPerSec(int numMessagesPerSec) {
        return new MessageFlooderUpdate(bftNode, numMessagesPerSec, commandSize);
    }

    public MessageFlooderUpdate commandSize(int commandSize) {
        return new MessageFlooderUpdate(bftNode, messagesPerSec, commandSize);
    }

    public Optional<Integer> getCommandSize() {
        return commandSize <= 0 ? Optional.empty() : Optional.of(commandSize);
    }

    public Optional<Integer> getMessagesPerSec() {
        return messagesPerSec <= 0 ? Optional.empty() : Optional.of(messagesPerSec);
    }

    public Optional<BFTNode> getBFTNode() {
        return Optional.ofNullable(bftNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bftNode, messagesPerSec, commandSize);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MessageFlooderUpdate)) {
            return false;
        }

        MessageFlooderUpdate other = (MessageFlooderUpdate) o;
        return Objects.equals(this.bftNode, other.bftNode)
            && this.commandSize == other.commandSize
            && this.messagesPerSec == other.messagesPerSec;
    }
}
