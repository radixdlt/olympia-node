package org.radix.time;

public interface Chronologic
{
	public long getTimestamp();
	public long getTimestamp(String name);
	public void setTimestamp(String name, long timestamp);
}
