package org.radix.modules.exceptions;

import org.radix.modules.Module;

@SuppressWarnings("serial")
public class ModuleRestartException extends ModuleException 
{
	public ModuleRestartException(Module module)
	{
		super (module);
	}

	public ModuleRestartException(String message, Throwable ex, Module module)
	{
		super (message, ex, module);
	}

	public ModuleRestartException(String message, Module module)
	{
		super (message, module);
	}

	public ModuleRestartException(Throwable ex, Module module)
	{
		super (ex, module);
	}
}