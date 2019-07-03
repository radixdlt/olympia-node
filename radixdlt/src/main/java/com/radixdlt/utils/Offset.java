package com.radixdlt.utils;

public enum Offset {
	PREVIOUS(-1), NONE(0), NEXT(1);
	
	private final int offset;
	
	private Offset(int offset)
	{
		this.offset = offset;
	}
	
	public int getOffset()
	{
		return this.offset;
	}
}
