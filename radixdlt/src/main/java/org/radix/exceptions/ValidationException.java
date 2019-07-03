package org.radix.exceptions;

import org.radix.common.Criticality;

@SuppressWarnings("serial")
public class ValidationException extends Exception
{
	private Criticality			criticality = Criticality.INFO;

	public ValidationException(Throwable cause)
	{
		super(cause);
		
		criticality = Criticality.CRITICAL;
	}

	public ValidationException(String message, Throwable cause, Criticality criticality)
	{
		super(message, cause);
		
		this.criticality = criticality;
	}

	public ValidationException(String message, Throwable cause) 
	{ 
		super(message, cause); 

		criticality = Criticality.CRITICAL;
	}

	public ValidationException(String message)
	{
		super(message);

		criticality = Criticality.CRITICAL;
	}
	
	public ValidationException(String message, Criticality criticality) 
	{
		super(message);

		this.criticality = criticality;
	}

	public ValidationException(Throwable cause, Criticality criticality) 
	{
		super(cause);
		
		this.criticality = criticality;
	}

	public Criticality getCriticality() { 
		return this.criticality; 
	}

	public void escalateCriticality() 
	{
		if (this.criticality == Criticality.FATAL)
			return;
		
		this.criticality = Criticality.values()[this.criticality.ordinal()+1];
	}
}
