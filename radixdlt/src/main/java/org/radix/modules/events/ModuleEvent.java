package org.radix.modules.events;

import org.radix.events.Event;
import org.radix.modules.Module;

public abstract class ModuleEvent extends Event
{
	private final Module module;
	
	public ModuleEvent(Module module) 
	{
		super();
		
		this.module = module;
	}

	public Module getModule() 
	{ 
		return module; 
	}
}
