package org.radix.atoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.radixdlt.tempo.Tempo;
import org.radix.atoms.particles.conflict.ParticleConflictHandler;
import org.radix.atoms.particles.conflict.ParticleConflictStore;
import org.radix.atoms.sync.AtomSync;
import org.radix.atoms.sync.AtomSyncStore;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.Shards;

public class Atoms extends Plugin
{
	@Override
	public List<Class<? extends Module>> getComponents()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<>();
		dependencies.add(AtomStore.class);
		dependencies.add(AtomSyncStore.class);
		dependencies.add(ParticleConflictStore.class);
		dependencies.add(Shards.class);
		if (!Modules.get(RuntimeProperties.class).has("tempo2.sync")) {
			dependencies.add(AtomSync.class);
		}
		dependencies.add(ParticleConflictHandler.class);
		dependencies.add(LocalAtomsProfiler.class);
		dependencies.add(GlobalAtomsProfiler.class);

		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void start_impl() throws ModuleException
	{ }

	@Override
	public void stop_impl() throws ModuleException
	{ }

	@Override
	public String getName() { return "Atoms"; }
}
