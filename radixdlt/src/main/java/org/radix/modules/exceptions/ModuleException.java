package org.radix.modules.exceptions;

import org.radix.modules.Module;

@SuppressWarnings("serial")
public class ModuleException extends Exception 
{
	private final Module module;
	
	public ModuleException (String arg0, Throwable arg1)
	{
		super (arg0, arg1);
		
		this.module = null;
	}

	public ModuleException (String arg0)
	{
		super (arg0);
		
		this.module = null;
	}

	public ModuleException (Throwable arg0)
	{
		super (arg0);
		
		this.module = null;
	}

	public ModuleException (Module module)
	{
		super ();
		
		this.module = module;
	}

	public ModuleException (String arg0, Throwable arg1, Module module)
	{
		super (arg0, arg1);
		
		this.module = module;
	}

	public ModuleException (String arg0, Module module)
	{
		super (arg0);
		
		this.module = module;
	}

	public ModuleException (Throwable arg0, Module module)
	{
		super (arg0);
		
		this.module = module;
	}
	
	public Module getModule() { return this.module; }
}