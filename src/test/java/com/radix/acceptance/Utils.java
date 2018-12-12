package com.radix.acceptance;

/**
 * Various utility classes.
 */
public class Utils {
	private Utils() {
		throw new IllegalArgumentException("Can't construct");
	}

	/**
	 * Output mapper that can be used in pipelines to show current state.
	 *
	 * @param value The value to print
	 * @return The passed-in value
	 */
	public static <T> T print(T value) {
		System.out.println(value);
		return value;
	}
}
