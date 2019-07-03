package org.radix.utils;

public class ExceptionUtils
{
	public static String toString(StackTraceElement[] stackTraceElements)
	{
		StringBuilder builder = new StringBuilder();

		for (StackTraceElement stackTraceElement : stackTraceElements)
			builder.append(stackTraceElement.toString()+System.lineSeparator());

		return builder.toString();
	}
}
