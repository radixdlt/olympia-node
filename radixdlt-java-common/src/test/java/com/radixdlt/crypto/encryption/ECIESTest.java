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

package com.radixdlt.crypto.encryption;

import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Bytes;

import static org.junit.Assert.assertArrayEquals;

import java.nio.charset.StandardCharsets;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test vectors for ECIES encryption.
 * Here we use the same test vectors described on
 * <a href="https://www.di-mgt.com.au/sha_testvectors.html">
 * https://www.di-mgt.com.au/sha_testvectors.html
 * </a>, with the repeated "a" test reduced to 1,000 elements.
 */
public class ECIESTest {

	private final String testEphemeralKeyHex  = "b343e55d b235f09b 32e614a7 6ec2a752 11d8feac 1195ac67 ee29dee4 bd8d6e87";
	private final ECKeyPair testEphemeralKey  = createTestKey(this.testEphemeralKeyHex);
	private final String testEncryptionKeyHex = "aac4c70a ee652996 5b496373 aba40e55 85b133e1 cde28854 b59ca9a0 4c6d237d";
	private final ECKeyPair testEncryptionKey = createTestKey(this.testEncryptionKeyHex);
	private final byte[] testIV = new byte[] {
		0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
	};

	@BeforeClass
	public static void setupBouncyCastle() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testVector1() throws ECIESException {
		testEcies(
			"abc",
			  "000102030405060708090a0b0c0d0e0f2102fb5a2ca482d9db1a33a729119777c5f3531bf3ef11c0acd8dfcb0349f50a3c5000"
			+ "000010927e726f7f60090c2249377e054c295090d0a3f34f98639f866a34cdcfd8992cbce7019de6c006aa659ab90e0933eab6"
		);
	}

	@Test
	public void testVector2() throws ECIESException {
		testEcies(
			"",
			  "000102030405060708090a0b0c0d0e0f2102fb5a2ca482d9db1a33a729119777c5f3531bf3ef11c0acd8dfcb0349f50a3c5000"
			+ "00001038afebaeb895039b799966459a70e2f05cc48b1a945bfd59aff88d484e0f19121822e6eef22cabf0e6803d0c914a6205"
		);
	}

	@Test
	public void testVector3() throws ECIESException {
		testEcies(
			"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
			  "000102030405060708090a0b0c0d0e0f2102fb5a2ca482d9db1a33a729119777c5f3531bf3ef11c0acd8dfcb0349f50a3c5000"
			+ "0000405d5275ff62eccb2d5682d3181651e297a7f2cc5235c71ff1ebe533ee89cfac85c481afed4c529c03d91e0e536aff9ace"
			+ "a34db6774c1bf88fadd12057f08dd99c725fe40dd56b5321c87264261d2f11dffc27e3b240e5e465ec7b1629ffc8df74"
		);
	}

	@Test
	public void testVector4() throws ECIESException {
		testEcies(
			"abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
			  "000102030405060708090a0b0c0d0e0f2102fb5a2ca482d9db1a33a729119777c5f3531bf3ef11c0acd8dfcb0349f50a3c5000"
			+ "0000802f912fb745113c7505a75892fab69fefce3ecef61611987aa316372956e53145e04ad21e5240ba6600b23d9465561003"
			+ "871d499f0c13ce847d45e8ae44a547b16c75bd1592c81ef128be0976ae1c9ea1e002a7372176561d84cec8b6ce98cae21693c0"
			+ "739f4bc6f9cac39a97cf4ffe5c005e2ded52aab4ebda3bde4a9c951539d2cfdc7e557fd0956050948450208915f8d32e5c17ae"
			+ "fbcb8afcb0720ac79716"
		);
	}

	@Test
	public void testVector5() throws ECIESException {
		testEcies(
			"a".repeat(1_000),
			  "000102030405060708090a0b0c0d0e0f2102fb5a2ca482d9db1a33a729119777c5f3531bf3ef11c0acd8dfcb0349f50a3c5000"
			+ "0003f0af1f5694bfb75291427752772466fa07cb9cf876435003c8ac93a40063919ed2108ce8e426c55411ef86ddb67eb9647d"
			+ "87d1756784bf6ea0ecfa5d66231e77c54b0da3ace82fd06905c508d79a5a2b64b7973ae77eec1a98cba1ae4029ddd96f1abdba"
			+ "95bafb122bbe1c5ad1af73c8748555af781538c4119a64f0568f9be14db0d71c026a34e5a31a7b3e3668c4a8b19d272bb3a66c"
			+ "2e61a7a919fcc45393e149ec4ddc0a2a7701549877ff1ec8c6f85db108a2cd855061b70d6aaf731ad028ce4de02bfe054df539"
			+ "c188c41acc34e42cc2a1d4410516dc1a4a4bd0399689f3fceecdf347f56588b1ce1f940129e49b5eb7bb983d4d5d5d1d87e268"
			+ "20c6c2d1c7cfe173c429ba754d79fe66b430af6fff99503b7ba861139ddd64ed314dc631a290237b38fe71e9c71412b85cbff7"
			+ "544c72978b30601b4e3643d40af17ef133ed7e1a1ceed73583aacafe3611305cfbf9802ef2c024593b6967f8d82fd0bbb62818"
			+ "4c72e0fa8461432e7b0a9fb2f725b4ddb9340c268b6702afbaef68908a5f07448eda69e1e1ff68a27735889a2fd902466503d4"
			+ "320176451f1b85c8b85e1af81c97ae1120195882cef2b16a9fd4e197a16b71f1f08e3871079c609febd556ef5e81a37e748ae3"
			+ "b8194cea359479569de456cc373522093e286f34b5ea62d219013f2dfcc916373024dc30c1f71c3de114b0eb88ab387f81c646"
			+ "1b65fab92d25870036ee2dbcf5e1a71ca938b8966f400bf278ac5e95863ff315b7895e160637e6a86e3d4a0345e0d735753e68"
			+ "6c3a8b58bb859bf833b5fb92c952d48364057aa2eedc32f9dcc4e22ddc13f4f7d207a8f46d9da85c2232b1b90d8752c44a1c6d"
			+ "3f6a4b3e691a211191ea790a1541d37c3399c2f01c5eb05e345c846a026200778594edeca3f9b178b0f5c3aec41e5760aa740d"
			+ "75d50dfe3aa8c9aa78de8c3ebfa147bf3da178157e6f56cef276756e80e795a344c4ed814efd0f508dea416fd3e5156d301e24"
			+ "66589c70407d8452dd840a3cee041c593cb2cab6f4684c75c14d82f42eb20f5bff4b2ff7feca7a6c518c51d75a13d339e93a58"
			+ "1c8a5ac5e8522e5ea185eab35ba09138c5dd5cbedeb98f40481aa3d4c383e93abcc3b800077b0c5da6b8bbcda531958a20cdb0"
			+ "814c45edc4da9c9c83ae3c30c1faf9aa0b14c63d5c53d5aceb961e0dca0e8983b97090e5c091b16fb3aecc56cee13723238643"
			+ "828029f3f3063b17336c694fcb3f061828b55646ca8a80bd3bcc280aeb904653ee171df2d046b6d487337f7690273c3d1e40f8"
			+ "5bdeed7beee6c7753af7183d5286a3f9e4f7c21aeb491e5f8fa112712bc5c70b1075e8eef42e495edff2c266f4227f1bd25ebc"
			+ "07dcfc6e333883815b4825215c8092add9b60998ccbcc250c5f20a0d07e39079cd535ba1b4e34357bac13819dbc17d60dcfbef"
			+ "f1e2033cde0ee8ec820748f3dc3dc590cf9ca565a6ca4a"
		);
	}

	private void testEcies(String stringToEncrypt, String hexToCompare) throws ECIESException {
		byte[] bytesToCompare = fromHex(hexToCompare);
		byte[] bytesToEncrypt = stringToEncrypt.getBytes(StandardCharsets.US_ASCII);
		byte[] encrypted = ECIES.encrypt(
			bytesToEncrypt,
			this.testEncryptionKey.getPublicKey().getEcPoint(),
			this.testEphemeralKey,
			this.testIV
		);
		assertArrayEquals(encrypted, bytesToCompare);
		byte[] decrypted = ECIES.decrypt(encrypted, this.testEncryptionKey);
		assertArrayEquals(decrypted, bytesToEncrypt);
	}

	private static byte[] fromHex(String hex) {
		return Bytes.fromHexString(hex.replaceAll(" ", ""));
	}

	private static ECKeyPair createTestKey(String keyHex) {
		try {
			return ECKeyPair.fromPrivateKey(fromHex(keyHex));
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalStateException("Can't create test key", e);
		}
	}
}

