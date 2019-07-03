package org.radix.utils;

public final class MathUtils
{
	private MathUtils() {
		throw new IllegalStateException("Can't construct");
	}

	public final static int log2(int value)
	{
		return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(value);
	}

	public final static int log2(long value)
	{
		return (Long.SIZE - 1) - Long.numberOfLeadingZeros(value);
	}

	public final static int roundUpBase2(int value)
	{
		value--;
		value |= value >> 1;
		value |= value >> 2;
		value |= value >> 4;
		value |= value >> 8;
		value |= value >> 16;
		value++;
		return value;
	}

	public final static int roundDownBase2(int value)
	{
		value |= value >> 1;
		value |= value >> 2;
		value |= value >> 4;
		value |= value >> 8;
		value |= value >> 16;
		return value;
	}
}
