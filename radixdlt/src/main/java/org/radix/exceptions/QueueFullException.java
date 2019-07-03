package org.radix.exceptions;

import java.io.IOException;

@SuppressWarnings("serial")
public class QueueFullException extends IOException
{
	public QueueFullException(String message)
	{
		super(message);
	}

	public QueueFullException(Throwable throwable)
	{
		super(throwable);
	}

	public QueueFullException(String message, Throwable throwable)
	{
		super(message, throwable);
	}
}
