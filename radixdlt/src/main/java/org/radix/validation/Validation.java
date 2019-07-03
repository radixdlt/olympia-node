package org.radix.validation;

import com.radixdlt.atomos.AtomDriver;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokenDefinitionConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokenInstancesConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import org.radix.time.Time;
import com.radixdlt.universe.Universe;

public class Validation extends Plugin {
	private static final Logger log = Logging.getLogger();

	@Override
	public void start_impl() throws ModuleException {
		final CMAtomOS os = new CMAtomOS(
			() -> Modules.get(Universe.class),
			Time::currentTimestamp
		);
		final boolean skipAtomFeeCheck = Modules.isAvailable(RuntimeProperties.class)
			&& Modules.get(RuntimeProperties.class).get("debug.nopow", false);
		os.loadKernelConstraintScrypt(new AtomDriver(Modules.get(Serialization.class), skipAtomFeeCheck, Time.MAXIMUM_DRIFT));
		os.load(new TokenDefinitionConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());
		os.load(new TokenInstancesConstraintScrypt());

		ConstraintMachine constraintMachine = os.buildMachine();

		Modules.getInstance().start(new ValidationHandler(constraintMachine));
	}

	@Override
	public void stop_impl() { /* not required */ }

	@Override
	public String getName() { return "Validation"; }
}
