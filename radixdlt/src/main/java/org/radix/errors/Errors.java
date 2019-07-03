package org.radix.errors;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Errors
{
	UNKNOWN				(-1, "Unknown"),
	NO_ERROR			(200, "Success"),

	// Not found / Missing errors //
	MISSING_PARAMETER	(401, "Missing parameter"),
	INVALID_PARAMETER	(402, "Invalid parameter"),
	NOT_FOUND			(404, "Not Found"),

	// Error / Failures //
	GENERAL_ERROR		(500, "General Error"),
	ATOM_ERROR			(501, "Atom Error"),
	ADDRESS_ERROR		(503, "Address Error"),
	KEY_ERROR			(504, "Key Error"),
	IO_ERROR			(506, "IO Error"),
	VERSION_ERROR		(508, "Version Error"),
	SECURITY_ERROR		(509, "Security Error"),
	ACCOUNT_ERROR		(510, "Account Error"),
	NETWORK_ERROR		(511, "Network Error"),
	USER_ERROR			(512, "User Error"),
	VALIDATION_ERROR	(513, "Validation Error"),
	CRYPTO_ERROR		(514, "Cryptography Error"),
	DISCOVERY_ERROR		(515, "Discovery Error"),

	// Errors / Failures specific to cards //
	CARD_ERROR			(590, "Card Exception"),

	// Warnings //
	GENERAL_WARNING		(600, "General Warning"),
	ATOM_WARNING		(601, "Transaction Warning");

	private final int code;
	private final String hint;

	Errors(int code, String hint) { this.code = code; this.hint = hint; }

	public int code() { return code; }
	public String hint() { return hint; }

	// STATICS //
	public static Errors get(int code)
	{
		for (Errors err : values())
			if (err.code == code)
				return err;

		return null;
	}

	public static boolean isError(int code)
	{
		if (code / 100 == 5) return true;

		return false;
	}

	public static boolean isWarning(int code)
	{
		if (code / 100 == 6) return true;

		return false;
	}

	@JsonValue
	@Override
	public String toString() {
		return this.name();
	}
}
