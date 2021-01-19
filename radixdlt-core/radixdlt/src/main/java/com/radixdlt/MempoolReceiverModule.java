package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.mempool.MempoolReceiver;

public class MempoolReceiverModule extends AbstractModule {
    @Override
    public void configure() {
        MapBinder<String, ModuleRunner> moduleRunners = MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class);
        moduleRunners.addBinding("mempool").to(MempoolReceiver.class).in(Scopes.SINGLETON);
    }
}
