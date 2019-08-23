package org.radix.validation;

import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.atomos.SimpleRadixEngineAtom;
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
		final CMAtomOS os = new CMAtomOS();
		os.load(new TokensConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());

		final ConstraintMachine constraintMachine = os.buildMachine();
		final RadixEngine<SimpleRadixEngineAtom> radixEngine = new RadixEngine<>(
			constraintMachine,
			atomStore
		);

		Modules.getInstance().start(new ValidationHandler(radixEngine));
	}

	@Override
	public void stop_impl() { /* not required */ }

	@Override
	public String getName() { return "Validation"; }
}
