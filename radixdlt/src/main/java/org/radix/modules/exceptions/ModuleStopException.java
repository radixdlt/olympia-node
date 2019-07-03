package org.radix.modules.exceptions;

import org.radix.modules.Module;

@SuppressWarnings("serial")
public class ModuleStopException extends ModuleException 
{
	public ModuleStopException(Module module)
	{
		super (module);
	}

	public ModuleStopException(String message, Throwable ex, Module module)
	{
		super (message, ex, module);
	}

	public ModuleStopException(String message, Module module)
	{
		super (message, module);
	}

	public ModuleStopException(Throwable ex, Module module)
	{
		super (ex, module);
	}
}