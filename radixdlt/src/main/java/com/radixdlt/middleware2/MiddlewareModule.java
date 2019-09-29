package com.radixdlt.middleware2;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.processing.EngineAtomEventListener;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import com.radixdlt.middleware2.store.LedgerEngineStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.universe.Universe;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;

import java.util.function.UnaryOperator;

public class MiddlewareModule extends AbstractModule {
    private static final Logger log = Logging.getLogger("MiddlewareModule");

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

    private static class RadixEngineProvider implements Provider<RadixEngine<SimpleRadixEngineAtom>> {
        private ConstraintMachine constraintMachine;
        private UnaryOperator unaryOperator;
        private EngineStore engineStore;
        private SimpleRadixEngineAtomToEngineAtom simpleRadixEngineAtomToEngineAtom;

        @Inject
        public RadixEngineProvider(ConstraintMachine constraintMachine, UnaryOperator unaryOperator, EngineStore engineStore, SimpleRadixEngineAtomToEngineAtom simpleRadixEngineAtomToEngineAtom) {
            this.constraintMachine = constraintMachine;
            this.unaryOperator = unaryOperator;
            this.engineStore = engineStore;
            this.simpleRadixEngineAtomToEngineAtom = simpleRadixEngineAtomToEngineAtom;
        }

        @Override
        public RadixEngine<SimpleRadixEngineAtom> get() {
            RadixEngine<SimpleRadixEngineAtom> radixEngine = new RadixEngine<SimpleRadixEngineAtom>(
                    constraintMachine,
                    unaryOperator,
                    engineStore
            );
            radixEngine.addAtomEventListener(new EngineAtomEventListener());
            radixEngine.start();
            return radixEngine;
        }
    }

    @Override
    protected void configure() {
        CMAtomOS os = buildCMAtomOS();
        ConstraintMachine constraintMachine = buildConstraintMachine(os);

        bind(ConstraintMachine.class).toInstance(constraintMachine);
        bind(UnaryOperator.class).toInstance(os.buildVirtualLayer());
        bind(EngineStore.class).to(LedgerEngineStore.class);
        bind(new TypeLiteral<RadixEngine<SimpleRadixEngineAtom>>() {
        }).toProvider(RadixEngineProvider.class).in(Scopes.SINGLETON);
    }
}