/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.messaging;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.radix.network.messaging.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MessageCentralMockProvider {

    private MessageCentralMockProvider() {
    }

    public static MessageCentral get() {
        final PublishProcessor<MessageFromPeer<?>> messageProcessor = PublishProcessor.create();
        final MessageCentral messageCentral = mock(MessageCentral.class);

        doAnswer(invocation -> {
            messageProcessor.onNext(new MessageFromPeer<Message>(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(messageCentral).send(any(), any());

        doAnswer(invocation ->
            Flowable.fromPublisher(messageProcessor)
                .filter(p -> ((Class<?>) invocation.getArgument(0)).isInstance(p.getMessage()))
        ).when(messageCentral).messagesOf(any());

        return messageCentral;
    }

}
