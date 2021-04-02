package com.radixdlt.crypto;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.utils.Bytes;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ECDSASignatureTest {

	@BeforeClass
	public static void startRadixTest() {
		TestSetupUtils.installBouncyCastleProvider();
	}


	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ECDSASignature.class)
				.suppress(Warning.NONFINAL_FIELDS) // serialization prevents us from making `r` and `s` final.
				.verify();
	}

	@Test
	public void test_rfc6979_determinstic_signatures() throws Exception {

		/// Sanity checks of Signing implementation of RFC6979 - Deterministic usage of ECDSA: https://tools.ietf.org/html/rfc6979
		/// Test vectors: https://github.com/trezor/trezor-crypto/blob/957b8129bded180c8ac3106e61ff79a1a3df8893/tests/test_check.c#L1959-L1965
		/// Signature data from: https://github.com/oleganza/CoreBitcoin/blob/master/CoreBitcoinTestsOSX/BTCKeyTests.swift

		//CHECKSTYLE:OFF
		//language=JSON
		String rfc6979TestVectors = "[\n" +
				"  {\n" +
				"    \"expectedDer\": \"3045022100af340daf02cc15c8d5d08d7735dfe6b98a474ed373bdb5fbecf7571be52b3842022050"
					+ "09fb27f37034a9b24b707b7c6b79ca23ddef9e25f7282e8a797efe53a8f124\",\n" +
				"    \"expectedSignatureR\": \"af340daf02cc15c8d5d08d7735dfe6b98a474ed373bdb5fbecf7571be52b3842\",\n" +
				"    \"expectedSignatureS\": \"5009fb27f37034a9b24b707b7c6b79ca23ddef9e25f7282e8a797efe53a8f124\",\n" +
				"    \"privateKey\": \"CCA9FBCC1B41E5A95D369EAA6DDCFF73B61A4EFAA279CFC6567E8DAA39CBAF50\",\n" +
				"    \"expectedPublicKeyCompressed\": \"0391f1ed66d63e12df118095ae010152f6cf65ffee656831f3000c28c4421d8e5b\",\n" +
				"    \"expectedK\": \"2df40ca70e639d89528a6b670d9d48d9165fdc0febc0974056bdce192b8e16a3\",\n" +
				"    \"message\": \"sample\",\n" +
				"    \"expectedHashOfMessage\": \"af2bdbe1aa9b6ec1e2ade1d694f41fc71a831d0268e9891562113d8a62add1bf\"\n" +
				"  },\n" +
				"  {\n" +
				"    \"expectedDer\": \"3045022100934b1ea10a4b3c1757e2b0c017d0b6143ce3c9a7e6a4a49860d7a6ab210ee3d8022024"
					+ "42ce9d2b916064108014783e923ec36b49743e2ffa1c4496f01a512aafd9e5\",\n" +
				"    \"expectedSignatureR\": \"934b1ea10a4b3c1757e2b0c017d0b6143ce3c9a7e6a4a49860d7a6ab210ee3d8\",\n" +
				"    \"expectedSignatureS\": \"2442ce9d2b916064108014783e923ec36b49743e2ffa1c4496f01a512aafd9e5\",\n" +
				"    \"privateKey\": \"0000000000000000000000000000000000000000000000000000000000000001\",\n" +
				"    \"expectedPublicKeyCompressed\": \"0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\n" +
				"    \"expectedK\": \"8f8a276c19f4149656b280621e358cce24f5f52542772691ee69063b74f15d15\",\n" +
				"    \"message\": \"Satoshi Nakamoto\",\n" +
				"    \"expectedHashOfMessage\": \"a0dc65ffca799873cbea0ac274015b9526505daaaed385155425f7337704883e\"\n" +
				"  },\n" +
				"  {\n" +
				"    \"expectedDer\": \"3045022100fd567d121db66e382991534ada77a6bd3106f0a1098c231e47993447cd6af2d002206b"
					+ "39cd0eb1bc8603e159ef5c20a5c8ad685a45b06ce9bebed3f153d10d93bed5\",\n" +
				"    \"expectedSignatureR\": \"fd567d121db66e382991534ada77a6bd3106f0a1098c231e47993447cd6af2d0\",\n" +
				"    \"expectedSignatureS\": \"6b39cd0eb1bc8603e159ef5c20a5c8ad685a45b06ce9bebed3f153d10d93bed5\",\n" +
				"    \"privateKey\": \"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140\",\n" +
				"    \"expectedPublicKeyCompressed\": \"0379be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\n" +
				"    \"expectedK\": \"33a19b60e25fb6f4435af53a3d42d493644827367e6453928554f43e49aa6f90\",\n" +
				"    \"message\": \"Satoshi Nakamoto\",\n" +
				"    \"expectedHashOfMessage\": \"a0dc65ffca799873cbea0ac274015b9526505daaaed385155425f7337704883e\"\n" +
				"  },\n" +
				"  {\n" +
				"    \"expectedDer\": \"304402207063ae83e7f62bbb171798131b4a0564b956930092b33b07b395615d9ec7e15c022058df"
					+ "cc1e00a35e1572f366ffe34ba0fc47db1e7189759b9fb233c5b05ab388ea\",\n" +
				"    \"expectedSignatureR\": \"7063ae83e7f62bbb171798131b4a0564b956930092b33b07b395615d9ec7e15c\",\n" +
				"    \"expectedSignatureS\": \"58dfcc1e00a35e1572f366ffe34ba0fc47db1e7189759b9fb233c5b05ab388ea\",\n" +
				"    \"privateKey\": \"f8b8af8ce3c7cca5e300d33939540c10d45ce001b8f252bfbc57ba0342904181\",\n" +
				"    \"expectedPublicKeyCompressed\": \"0292df7b245b81aa637ab4e867c8d511008f79161a97d64f2ac709600352f7acbc\",\n" +
				"    \"expectedK\": \"525a82b70e67874398067543fd84c83d30c175fdc45fdeee082fe13b1d7cfdf1\",\n" +
				"    \"message\": \"Alan Turing\",\n" +
				"    \"expectedHashOfMessage\": \"4ba38d48a60f1b29e9eb726eaff08b2e83d8d81e031666fee50e85900d7dc1ef\"\n" +
				"  },\n" +
				"  {\n" +
				"    \"expectedDer\": \"30450221008600dbd41e348fe5c9465ab92d23e3db8b98b873beecd930736488696438cb6b022054"
					+ "7fe64427496db33bf66019dacbf0039c04199abb0122918601db38a72cfc21\",\n" +
				"    \"expectedSignatureR\": \"8600dbd41e348fe5c9465ab92d23e3db8b98b873beecd930736488696438cb6b\",\n" +
				"    \"expectedSignatureS\": \"547fe64427496db33bf66019dacbf0039c04199abb0122918601db38a72cfc21\",\n" +
				"    \"privateKey\": \"0000000000000000000000000000000000000000000000000000000000000001\",\n" +
				"    \"expectedPublicKeyCompressed\": \"0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\n" +
				"    \"expectedK\": \"38aa22d72376b4dbc472e06c3ba403ee0a394da63fc58d88686c611aba98d6b3\",\n" +
				"    \"message\": \"All those moments will be lost in time, like tears in rain. Time to die...\",\n" +
				"    \"expectedHashOfMessage\": \"7d1833f54854ac51659521afcd0ec6dca2ce2351429614bfa28a756b1b3c637f\"\n" +
				"  },\n" +
				"  {\n" +
				"    \"expectedDer\": \"3045022100b552edd27580141f3b2a5463048cb7cd3e047b97c9f98076c32dbdf85a68718b022027"
					+ "9fa72dd19bfae05577e06c7c0c1900c371fcd5893f7e1d56a37d30174671f6\",\n" +
				"    \"expectedSignatureR\": \"b552edd27580141f3b2a5463048cb7cd3e047b97c9f98076c32dbdf85a68718b\",\n" +
				"    \"expectedSignatureS\": \"279fa72dd19bfae05577e06c7c0c1900c371fcd5893f7e1d56a37d30174671f6\",\n" +
				"    \"privateKey\": \"e91671c46231f833a6406ccbea0e3e392c76c167bac1cb013f6f1013980455c2\",\n" +
				"    \"expectedPublicKeyCompressed\": \"03567b7512001f3cc4dcb8b8096c046fff571ab07adb2126cd42908f2ff1ca424a\",\n" +
				"    \"expectedK\": \"1f4b84c23a86a221d233f2521be018d9318639d5b8bbd6374a8a59232d16ad3d\",\n" +
				"    \"message\": \"There is a computer disease that anybody who works with computers knows about. It's "
					+ "a very serious disease and it interferes completely with the work. The trouble with computers is "
					+ "that you 'play' with them!\",\n" +
				"    \"expectedHashOfMessage\": \"1609a53bb33ef00e0cc1e784b436d7924956d87ec2b399574378312f07cba3e8\"\n" +
				"  }\n" +
				"]";
		//CHECKSTYLE:ON

		var type = new TypeToken<ArrayList<Map<String, String>>>() {}.getType();

		ArrayList<Map<String, String>> vectors = new Gson().fromJson(rfc6979TestVectors, type);

		assertEquals(6, vectors.size());

		for (var vector : vectors) {
			var keyPair = ECKeyPair.fromPrivateKey(Bytes.fromHexString(vector.get("privateKey")));
			var publicKey = keyPair.getPublicKey();

			assertEquals(vector.get("expectedPublicKeyCompressed"), Bytes.toHexString(publicKey.getBytes()));

			var messageUnhashedPlaintext = vector.get("message");
			var messageUnhashed = messageUnhashedPlaintext.getBytes(StandardCharsets.UTF_8);
			var message = sha2bits256Once(messageUnhashed);

			assertEquals(vector.get("expectedHashOfMessage"), Bytes.toHexString(message));

			var signature = keyPair.sign(message, true, true);

			assertEquals(vector.get("expectedSignatureR"), signature.getR().toString(16));
			assertEquals(vector.get("expectedSignatureS"), signature.getS().toString(16));
			assertTrue("Should verify", publicKey.verify(message, signature));

			var expectedSignatureDERBytes = Bytes.fromHexString(vector.get("expectedDer"));
			var sigFromDER = ECDSASignature.decodeFromDER(expectedSignatureDERBytes);

			assertEquals(sigFromDER, signature);
		}
	}

	private byte[] sha2bits256Once(byte[] data) {
		MessageDigest hasher = null;
		try {
			hasher = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Should always be able to sha256 hash", e);
		}
		hasher.update(data);
		return hasher.digest();
	}
}
