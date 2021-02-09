package com.radixdlt.chaos.messageflooder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.radixdlt.environment.EventProcessor;

public class MessageFlooderModule extends AbstractModule {
    @Override
    public void configure() {
        bind(MessageFlooder.class).in(Scopes.SINGLETON);
    }

    @Provides
    public EventProcessor<MessageFlooderUpdate> messageFloodUpdateEventProcessor(MessageFlooder messageFlooder) {
        return messageFlooder.messageFloodUpdateProcessor();
    }

    @Provides
    public EventProcessor<ScheduledMessageFlood> scheduledMessageFloodEventProcessor(MessageFlooder messageFlooder) {
        return messageFlooder.scheduledMessageFloodProcessor();
    }
}
