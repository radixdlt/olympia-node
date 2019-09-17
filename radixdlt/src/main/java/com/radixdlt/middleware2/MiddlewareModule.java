package com.radixdlt.middleware2;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.AtomContent;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.AtomContentToImmutableAtomConverter;
import com.radixdlt.middleware2.converters.TempoAtomContentToImmutableAtomConverter;
import com.radixdlt.middleware2.processing.AtomProcessor;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.middleware2.store.LedgerEngineStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.tempo.TempoAtomContent;
import com.radixdlt.universe.Universe;
import org.radix.modules.Modules;

import java.util.function.UnaryOperator;

public class MiddlewareModule extends AbstractModule {

    private CMAtomOS buildCMAtomOS() {
        final CMAtomOS os = new CMAtomOS(addr -> {
            final int universeMagic = Modules.get(Universe.class).getMagic() & 0xff;
            if (addr.getMagic() != universeMagic) {
                return Result.error("Address magic " + addr.getMagic() + " does not match universe " + universeMagic);
            }

            return Result.success();
        });
        os.load(new TokensConstraintScrypt());
        os.load(new UniqueParticleConstraintScrypt());
        os.load(new MessageParticleConstraintScrypt());
        return os;
    }

    private ConstraintMachine buildConstraintMachine(CMAtomOS os) {

        final ConstraintMachine constraintMachine = new ConstraintMachine.Builder()
                .setParticleTransitionProcedures(os.buildTransitionProcedures())
                .setParticleStaticCheck(os.buildParticleStaticCheck())
                .build();
        return constraintMachine;
    }

    @Provides
    private RadixEngine<SimpleRadixEngineAtom> getRadixEngine(Injector injector) {
        return new RadixEngine<SimpleRadixEngineAtom>(
                injector.getInstance(ConstraintMachine.class),
                injector.getInstance(UnaryOperator.class),
                injector.getInstance(EngineStore.class)
        );
    }

    @Override
    protected void configure() {
        CMAtomOS os = buildCMAtomOS();
        ConstraintMachine constraintMachine = buildConstraintMachine(os);

        bind(ConstraintMachine.class).toInstance(constraintMachine);
        bind(UnaryOperator.class).toInstance(os.buildVirtualLayer());
        bind(EngineStore.class).to(LedgerEngineStore.class);

        bind(AtomProcessor.class).to(RadixEngineAtomProcessor.class);

        MapBinder<Class<? extends AtomContent>, AtomContentToImmutableAtomConverter> contentConverterBinder =
                MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends AtomContent>>() {
                }, new TypeLiteral<AtomContentToImmutableAtomConverter>() {
                });
        contentConverterBinder.addBinding(TempoAtomContent.class).to(TempoAtomContentToImmutableAtomConverter.class);
    }
}