package org.radix.modules.events;

import org.radix.modules.Module;

public final class ModuleStartedEvent extends ModuleEvent
{
	public ModuleStartedEvent(Module module)
	{
		super(module);
	}
}
