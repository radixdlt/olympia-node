package org.radix.modules.events;

import org.radix.modules.Module;

public class ModuleResetEvent extends ModuleEvent
{
	public ModuleResetEvent(Module module)
	{
		super(module);
	}
}
