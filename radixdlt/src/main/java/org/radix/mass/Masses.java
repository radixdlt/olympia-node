package org.radix.mass;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Masses extends Plugin
{
	private static final Logger log = Logging.getLogger();

	@Override
	public List<Class<? extends Module>> getComponents()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<>();
		dependencies.add(NodeMassStore.class);
		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void start_impl() throws ModuleException
	{
	}

	@Override
	public void stop_impl() throws ModuleException
	{
	}

	@Override
	public String getName()
	{
		return "Mass";
	}
}

