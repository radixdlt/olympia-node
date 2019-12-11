package org.radix.exceptions;

@SuppressWarnings("serial")
public class ValidationException extends Exception
{
	public ValidationException(Throwable cause)
	{
		super(cause);
	}

	public ValidationException(String message, Throwable cause)
	{ 
		super(message, cause); 
	}

	public ValidationException(String message)
	{
		super(message);
	}
}
