package com.radixdlt.serialization.mapper;

/**
 * Constants for DSON protocol encoded in CBOR/JSON.
 */
final class JacksonCodecConstants {
	private JacksonCodecConstants() {
		throw new IllegalStateException("Can't construct");
	}

	// Encodings for CBOR mappings
	static final byte BYTES_VALUE = 0x01;
	static final byte EUID_VALUE  = 0x02;
	static final byte HASH_VALUE  = 0x03;
	static final byte ADDR_VALUE  = 0x04;
	static final byte U20_VALUE   = 0x05; // 0x20 byte = 256 bit unsigned int
	static final byte RRI_VALUE   = 0x06;
	static final byte U30_VALUE   = 0x07; // 0x30 byte = 384 bit unsigned int
	static final byte AID_VALUE   = 0x08;

	// Type tag prefixes used in strings for JSON mappings
	static final int STR_VALUE_LEN     = 5;
	static final String BYTE_STR_VALUE = ":byt:";
	static final String EUID_STR_VALUE = ":uid:";
	static final String HASH_STR_VALUE = ":hsh:";
	static final String STR_STR_VALUE  = ":str:";
	static final String ADDR_STR_VALUE = ":adr:";
	static final String U20_STR_VALUE  = ":u20:"; // 0x20 byte = 256 bit unsigned int
	static final String RRI_STR_VALUE  = ":rri:";
	static final String U30_STR_VALUE  = ":u30:"; // 0x30 byte = 384 bit unsigned int
	static final String AID_STR_VALUE  = ":aid:";
}
