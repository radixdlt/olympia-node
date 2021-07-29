/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.identifiers;

import com.google.common.base.Suppliers;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Base58;
import com.radixdlt.utils.functional.Result;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import static com.radixdlt.identifiers.CommonErrors.INVALID_RADIX_ADDRESS;

/**
 * base58 address based on a public key
 */
public final class RadixAddress {
	/**
	 * The public key this address represents
	 */
	private final ECPublicKey publicKey;

	/**
	 * The unique string which maps this address represents
	 */
	private final byte[] addressBytes;

	/**
	 * The Base58 formatted string of this address
	 */
	private final Supplier<String> base58 = Suppliers.memoize(this::computeBase58);

	/**
	 * The magic byte of this address
	 */
	private final transient int magicByte;

	public RadixAddress(byte magic, ECPublicKey publicKey) {
		this.publicKey = Objects.requireNonNull(publicKey);

		byte[] digest = publicKey.getCompressedBytes();
		byte[] addressBytes = new byte[1 + digest.length + 4];
		addressBytes[0] = magic;
		System.arraycopy(digest, 0, addressBytes, 1, digest.length);
		byte[] check = HashUtils.sha256(addressBytes, 0, digest.length + 1).asBytes();
		System.arraycopy(check, 0, addressBytes, digest.length + 1, 4);

		this.addressBytes = addressBytes;
		this.magicByte = magic;
	}

	public static RadixAddress from(byte[] raw) {
		if (raw.length != 1 + ECPublicKey.COMPRESSED_BYTES + 4) {
			throw new IllegalArgumentException("Invalid number of bytes for address");
		}

		try {
			byte[] check = HashUtils.sha256(raw, 0, raw.length - 4).asBytes();
			for (int i = 0; i < 4; ++i) {
				if (check[i] != raw[raw.length - 4 + i]) {
					throw new IllegalArgumentException("Address " + Base58.toBase58(raw) + " checksum mismatch");
				}
			}

			byte[] digest = new byte[raw.length - 5];
			System.arraycopy(raw, 1, digest, 0, raw.length - 5);

			return new RadixAddress(raw[0], ECPublicKey.fromBytes(digest));
		} catch (PublicKeyException e) {
			throw new IllegalArgumentException("Unable to create address from string: " + Base58.toBase58(raw), e);
		}
	}

	public boolean ownedBy(ECPublicKey ecPublicKey) {
		return publicKey.equals(ecPublicKey);
	}

	public int getMagic() {
		return addressBytes[0];
	}

	public static RadixAddress from(String address) {
		byte[] raw = Base58.fromBase58(address);
		return from(raw);
	}

	public static Result<RadixAddress> fromString(String address) {
		return Result.wrap(INVALID_RADIX_ADDRESS, () -> from(Base58.fromBase58(address)));
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(addressBytes, addressBytes.length);
	}

	public ECPublicKey getPublicKey() {
		return this.publicKey;
	}

	public EUID euid() {
		return this.publicKey.euid();
	}

	@Override
	public String toString() {
		return this.base58.get();
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(addressBytes);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof RadixAddress) {
			RadixAddress other = (RadixAddress) o;
			return Arrays.equals(this.addressBytes, other.addressBytes);
		}
		return false;
	}

	private String computeBase58() {
		return Base58.toBase58(addressBytes);
	}

	// ###  Methods from Client Library ###

	public int getMagicByte() {
		return magicByte;
	}
}
