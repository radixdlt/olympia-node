/* Copyright 2021 Radix DLT Ltd incorporated in England.
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

package com.radixdlt.crypto.hdwallet;

import com.google.common.base.Objects;
import org.bitcoinj.crypto.ChildNumber;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A BIP32 path wrapping underlying implementation using BitcoinJ.
 */
final class BitcoinJBIP32Path implements HDPath {

	private static final String BIP32_HARDENED_MARKER_BITCOINJ = "H";

	private final org.bitcoinj.crypto.HDPath path;

	private BitcoinJBIP32Path(org.bitcoinj.crypto.HDPath path) {
		this.path = path;
	}

	static BitcoinJBIP32Path fromPath(HDPath path) {
		try {
			return fromString(path.toString());
		} catch (HDPathException e) {
			throw new IllegalStateException("String representation of any path should be correct.", e);
		}
	}

	static BitcoinJBIP32Path fromString(String path) throws HDPathException {
		HDPaths.validateHDPathString(path);
		return new BitcoinJBIP32Path(org.bitcoinj.crypto.HDPath.parsePath(toBitcoinJPath(path)));
	}

	private static String toBitcoinJPath(String standardPath) {
		// For some reason BitcoinJ chose to not use standard notation of hardened path components....
		return standardPath.replace(HDPaths.BIP32_HARDENED_MARKER_STANDARD, BIP32_HARDENED_MARKER_BITCOINJ);
	}

	private static String standardizePath(String nonStandardPath) {
		// For some reason BitcoinJ chose to not use standard notation of hardened path components....
		return nonStandardPath.replace(BIP32_HARDENED_MARKER_BITCOINJ, HDPaths.BIP32_HARDENED_MARKER_STANDARD);
	}

	private int indexOfLastComponent() {
		if (depth() == 0) {
			throw new IllegalStateException("Trying to access component of a BIP32 path with 0 depth, this is undefined.");
		}
		return depth() - 1;
	}

	private ChildNumber lastComponent() {
		return path.get(indexOfLastComponent());
	}

	List<ChildNumber> componentsUpTo(int index) {
		return IntStream.range(0, index).mapToObj(path::get).collect(Collectors.toList());
	}

	List<ChildNumber> components() {
		return componentsUpTo(depth());
	}

	@Override
	public boolean isHardened() {
		return lastComponent().isHardened();
	}

	@Override
	public boolean hasPrivateKey() {
		return path.hasPrivateKey();
	}

	@Override
	public String toString() {
		return standardizePath(path.toString());
	}

	@Override
	public int depth() {
		return path.size();
	}

	@Override
	public long index() {
		long index = lastComponent().num();
		if (!isHardened()) {
			return index;
		}
		index += HDPaths.BIP32_HARDENED_VALUE_INCREMENT;
		return index;
	}

	@Override
	public HDPath next() {
		ArrayList<ChildNumber> nextPathComponents = new ArrayList<>(pathListFromBIP32Path(this, indexOfLastComponent()));
		nextPathComponents.add(new ChildNumber(lastComponent().num() + 1, lastComponent().isHardened()));
		org.bitcoinj.crypto.HDPath nextPath = new org.bitcoinj.crypto.HDPath(this.hasPrivateKey(), nextPathComponents);
		return new BitcoinJBIP32Path(nextPath);
	}

	private static List<ChildNumber> pathListFromBIP32Path(BitcoinJBIP32Path path, @Nullable Integer toIndex) {
		return path.componentsUpTo(toIndex == null ? path.indexOfLastComponent() : toIndex);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		BitcoinJBIP32Path that = (BitcoinJBIP32Path) o;
		return Objects.equal(path, that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(path);
	}
}
