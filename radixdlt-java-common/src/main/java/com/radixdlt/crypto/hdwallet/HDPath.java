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

/**
 * A path use to deterministically derive a key pair in some hierarchy given by some root key. The path is
 * typically <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a> compliant.
 */
public interface HDPath {

	/**
	 * The string representation of the BIP32 path, using standard notation "'" for hardened components, e.g.
	 * "m/44'/536'/2'/1/4
	 * @return a string representation of the BIP32 path, using standard notation "'" for hardened components, e.g.
	 * "m/44'/536'/2'/1/4
	 */
	@Override
	String toString();

	/**
	 * Is this a path to a private key?
	 *
	 * @return true if yes, false if no or a partial path
	 */
	boolean hasPrivateKey();

	/**
	 * Whether the last component in the path is "hardened" or not, if the last component is not hardened, it does not mean
	 * that potentially earlier components are not hardened as well, i.e. this only looks at the <b>last</b> component.
	 * @return whether the last component in the path is "hardened" or not, if the last component is not hardened, it does not mean
	 * that potentially earlier components are not hardened as well, i.e. this only looks at the <b>last</b> component.
	 */
	boolean isHardened();

	/**
	 * The number of components in the path, `1` is the lowest possible value, and most commonly 5 is the max depth, even though BIP32
	 * supports a longer depth. The depth of "m/0" is 1, the depth of "m/0'/1" is 2 etc.
	 * @return number of components in the path, `1` is the lowest possible value, and most commonly 5 is the max depth, even though BIP32
	 * supports a longer depth. The depth of "m/0" is 1, the depth of "m/0'/1" is 2 etc.
	 */
	int depth();

	/**
	 * Returns the value of the last component, taking into account if it is hardened or not, i.e. the index of the path "m/0/0" is 0, but
	 * the index of the path "m/0/0'" - which is hardened - is 2147483648
	 * (0 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}) -
	 * and the index of "m/0/1'" is 2147483649
	 * (1 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}).
	 *
	 * @return the value of the last component, taking into account if it is hardened or not, i.e. the index of the path "m/0/0" is 0, but
	 * the index of the path "m/0/0'" - which is hardened - is 2147483648
	 * (0 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}) -
	 * and the index of "m/0/1'" is 2147483649
	 * (1 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}).
	 */
	long index();


	/**
	 * Returns the path to the subsequent child key key pair, e.g. identical to this path but with the value of {@link #index() + 1}.
	 * @return the path to the subsequent child key key pair, e.g. identical to this path but with the value of {@link #index()} + 1.
	 */
	HDPath next();
}
