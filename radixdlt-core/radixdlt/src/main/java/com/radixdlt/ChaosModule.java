package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.chaos.BFTNodeMessageFlooder;
import com.radixdlt.chaos.ChaosRunner;
import com.radixdlt.chaos.MessageFloodUpdate;
import com.radixdlt.chaos.ScheduledMessageFlood;
import com.radixdlt.environment.EventProcessor;

public final class ChaosModule extends AbstractModule {
    @Override
    public void configure() {
        MapBinder<String, ModuleRunner> moduleRunners = MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class);
        moduleRunners.addBinding("chaos").to(ChaosRunner.class).in(Scopes.SINGLETON);
        bind(BFTNodeMessageFlooder.class).in(Scopes.SINGLETON);
    }

    @Provides
    public EventProcessor<MessageFloodUpdate> messageFloodUpdateEventProcessor(BFTNodeMessageFlooder bftNodeMessageFlooder) {
        return bftNodeMessageFlooder.messageFloodUpdateProcessor();
    }

    @Provides
    public EventProcessor<ScheduledMessageFlood> scheduledMessageFloodEventProcessor(BFTNodeMessageFlooder bftNodeMessageFlooder) {
        return bftNodeMessageFlooder.scheduledMessageFloodProcessor();
    }
}
