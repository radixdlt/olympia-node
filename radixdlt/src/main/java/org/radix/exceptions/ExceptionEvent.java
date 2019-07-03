package org.radix.exceptions;

import org.radix.events.Event;

public class ExceptionEvent extends Event 
{ 
	private final Throwable exception;
	
	public ExceptionEvent(Throwable exception)
	{
		super();
		
		this.exception = exception;
	}
	
	public Throwable getException()
	{
		return this.exception;
	}
}
