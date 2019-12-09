package org.radix.atoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.radix.modules.Module;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.shards.Shards;

public class Atoms extends Plugin
{
	@Override
	public List<Class<? extends Module>> getComponents()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<>();
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
