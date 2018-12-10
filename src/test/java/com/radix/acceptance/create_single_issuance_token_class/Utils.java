package com.radix.acceptance.create_single_issuance_token_class;

final class Utils {
	private Utils() {
		throw new IllegalStateException("Can't construct");
	}

	static <U> U print(U value) {
		System.out.println(value);
		return value;
	}
}
