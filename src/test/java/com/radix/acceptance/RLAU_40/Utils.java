package com.radix.acceptance.RLAU_40;

final class Utils {
	private Utils() {
		throw new IllegalStateException("Can't construct");
	}

	static <U> U print(U value) {
		System.out.println(value);
		return value;
	}
}
