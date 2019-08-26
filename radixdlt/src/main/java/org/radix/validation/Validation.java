package org.radix.validation;

import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atomos.AtomDriver;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import org.radix.atoms.AtomStore;
import org.radix.atoms.AtomEngineStore;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.RuntimeProperties;
import org.radix.time.Time;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

public class Validation extends Plugin {
	private final AtomEngineStore atomStore = new AtomEngineStore(
		() -> Modules.get(AtomStore.class),
		() -> LocalSystem.getInstance().getShards()
	);

	@Override
	public void start_impl() throws ModuleException {
		final CMAtomOS os = new CMAtomOS();
		final boolean skipAtomFeeCheck = Modules.isAvailable(RuntimeProperties.class)
			&& Modules.get(RuntimeProperties.class).get("debug.nopow", false);
		final AtomDriver atomDriver = new AtomDriver(
			() -> Modules.get(Universe.class),
			Time::currentTimestamp,
			skipAtomFeeCheck,
			Time.MAXIMUM_DRIFT
		);
		os.loadKernelConstraintScrypt(atomDriver);
		os.load(new TokensConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());

		final Pair<ConstraintMachine, AtomCompute> engineParams = os.buildMachine();
		final RadixEngine radixEngine = new RadixEngine(engineParams.getFirst(), engineParams.getSecond(), atomStore);

		Modules.getInstance().start(new ValidationHandler(radixEngine));
	}

	@Override
	public void stop_impl() { /* not required */ }

	@Override
	public String getName() { return "Validation"; }
}
