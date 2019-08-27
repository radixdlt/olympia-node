package org.radix.validation;

import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.universe.Universe;
import org.radix.atoms.AtomStore;
import org.radix.atoms.AtomEngineStore;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.universe.system.LocalSystem;

public class Validation extends Plugin {
	private final AtomEngineStore atomStore = new AtomEngineStore(
		() -> Modules.get(AtomStore.class),
		() -> LocalSystem.getInstance().getShards()
	);

	@Override
	public void start_impl() throws ModuleException {
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

		final ConstraintMachine constraintMachine = new ConstraintMachine.Builder()
			.setParticleProcedures(os.buildTransitionProcedures())
			.setWitnessValidators(os.buildWitnessValidators())
			.setParticleStaticCheck(os.buildParticleStaticCheck())
			.build();

		final RadixEngine<SimpleRadixEngineAtom> radixEngine = new RadixEngine<>(
			constraintMachine,
			os.buildVirtualLayer(),
			atomStore
		);

		Modules.getInstance().start(new ValidationHandler(radixEngine));
	}

	@Override
	public void stop_impl() { /* not required */ }

	@Override
	public String getName() { return "Validation"; }
}
