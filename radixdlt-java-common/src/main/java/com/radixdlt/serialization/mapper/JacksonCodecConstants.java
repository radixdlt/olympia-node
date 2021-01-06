/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
	static final byte LONGS_VALUE = 0x09;

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
	static final String LONGS_STR_VALUE  = ":lng:";
}
