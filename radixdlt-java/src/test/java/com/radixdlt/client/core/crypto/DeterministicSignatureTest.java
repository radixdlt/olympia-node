package com.radixdlt.client.core.crypto;

import com.radixdlt.client.core.util.Hash;
import org.junit.Test;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.radix.utils.primitives.Bytes;

public class DeterministicSignatureTest {

    @Test
    public void test_rfc6979_determinstic_signatures() {

        /// Sanity checks of Signing implementation of RFC6979 - Deterministic usage of ECDSA: https://tools.ietf.org/html/rfc6979
        /// Test vectors: https://github.com/trezor/trezor-crypto/blob/957b8129bded180c8ac3106e61ff79a1a3df8893/tests/test_check.c#L1959-L1965
        /// Signature data from: https://github.com/oleganza/CoreBitcoin/blob/master/CoreBitcoinTestsOSX/BTCKeyTests.swift

        //CHECKSTYLE:OFF
        //language=JSON
        String rfc6979TestVectors = "[\n" +
                "  {\n" +
                "    \"expectedDer\": \"3045022100af340daf02cc15c8d5d08d7735dfe6b98a474ed373bdb5fbecf7571be52b384202205009fb27f37034a9b24b707b7c6b79ca23ddef9e25f7282e8a797efe53a8f124\",\n" +
                "    \"expectedSignatureR\": \"af340daf02cc15c8d5d08d7735dfe6b98a474ed373bdb5fbecf7571be52b3842\",\n" +
                "    \"expectedSignatureS\": \"5009fb27f37034a9b24b707b7c6b79ca23ddef9e25f7282e8a797efe53a8f124\",\n" +
                "    \"key\": \"CCA9FBCC1B41E5A95D369EAA6DDCFF73B61A4EFAA279CFC6567E8DAA39CBAF50\",\n" +
                "    \"message\": \"sample\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"expectedDer\": \"3045022100934b1ea10a4b3c1757e2b0c017d0b6143ce3c9a7e6a4a49860d7a6ab210ee3d802202442ce9d2b916064108014783e923ec36b49743e2ffa1c4496f01a512aafd9e5\",\n" +
                "    \"expectedSignatureR\": \"934b1ea10a4b3c1757e2b0c017d0b6143ce3c9a7e6a4a49860d7a6ab210ee3d8\",\n" +
                "    \"expectedSignatureS\": \"2442ce9d2b916064108014783e923ec36b49743e2ffa1c4496f01a512aafd9e5\",\n" +
                "    \"key\": \"0000000000000000000000000000000000000000000000000000000000000001\",\n" +
                "    \"message\": \"Satoshi Nakamoto\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"expectedDer\": \"3045022100fd567d121db66e382991534ada77a6bd3106f0a1098c231e47993447cd6af2d002206b39cd0eb1bc8603e159ef5c20a5c8ad685a45b06ce9bebed3f153d10d93bed5\",\n" +
                "    \"expectedSignatureR\": \"fd567d121db66e382991534ada77a6bd3106f0a1098c231e47993447cd6af2d0\",\n" +
                "    \"expectedSignatureS\": \"6b39cd0eb1bc8603e159ef5c20a5c8ad685a45b06ce9bebed3f153d10d93bed5\",\n" +
                "    \"key\": \"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140\",\n" +
                "    \"message\": \"Satoshi Nakamoto\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"expectedDer\": \"304402207063ae83e7f62bbb171798131b4a0564b956930092b33b07b395615d9ec7e15c022058dfcc1e00a35e1572f366ffe34ba0fc47db1e7189759b9fb233c5b05ab388ea\",\n" +
                "    \"expectedSignatureR\": \"7063ae83e7f62bbb171798131b4a0564b956930092b33b07b395615d9ec7e15c\",\n" +
                "    \"expectedSignatureS\": \"58dfcc1e00a35e1572f366ffe34ba0fc47db1e7189759b9fb233c5b05ab388ea\",\n" +
                "    \"key\": \"f8b8af8ce3c7cca5e300d33939540c10d45ce001b8f252bfbc57ba0342904181\",\n" +
                "    \"message\": \"Alan Turing\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"expectedDer\": \"30450221008600dbd41e348fe5c9465ab92d23e3db8b98b873beecd930736488696438cb6b0220547fe64427496db33bf66019dacbf0039c04199abb0122918601db38a72cfc21\",\n" +
                "    \"expectedSignatureR\": \"8600dbd41e348fe5c9465ab92d23e3db8b98b873beecd930736488696438cb6b\",\n" +
                "    \"expectedSignatureS\": \"547fe64427496db33bf66019dacbf0039c04199abb0122918601db38a72cfc21\",\n" +
                "    \"key\": \"0000000000000000000000000000000000000000000000000000000000000001\",\n" +
                "    \"message\": \"All those moments will be lost in time, like tears in rain. Time to die...\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"expectedDer\": \"3045022100b552edd27580141f3b2a5463048cb7cd3e047b97c9f98076c32dbdf85a68718b0220279fa72dd19bfae05577e06c7c0c1900c371fcd5893f7e1d56a37d30174671f6\",\n" +
                "    \"expectedSignatureR\": \"b552edd27580141f3b2a5463048cb7cd3e047b97c9f98076c32dbdf85a68718b\",\n" +
                "    \"expectedSignatureS\": \"279fa72dd19bfae05577e06c7c0c1900c371fcd5893f7e1d56a37d30174671f6\",\n" +
                "    \"key\": \"e91671c46231f833a6406ccbea0e3e392c76c167bac1cb013f6f1013980455c2\",\n" +
                "    \"message\": \"There is a computer disease that anybody who works with computers knows about. It's a very serious disease and it interferes completely with the work. The trouble with computers is that you 'play' with them!\"\n" +
                "  }\n" +
                "]";
        //CHECKSTYLE:ON

        Type type = new TypeToken<ArrayList<Map<String, String>>>() { }.getType();
        ArrayList<Map<String, String>> vectors = new Gson().fromJson(rfc6979TestVectors, type);
        assertEquals(6, vectors.size());
        for (Map<String, String> vector : vectors) {
            ECKeyPair keyPair = new ECKeyPair(Bytes.fromHexString(vector.get("key")));
            byte[] messageUnhashed = vector.get("message").getBytes(StandardCharsets.UTF_8);
            byte[] message = Hash.sha256(messageUnhashed);
            ECSignature signature = keyPair.sign(message, true, true);
            assertEquals(vector.get("expectedSignatureR"), signature.getR().toString(16));
            assertEquals(vector.get("expectedSignatureS"), signature.getS().toString(16));
            assertTrue("Should verify", keyPair.getPublicKey().verify(message, signature));
        }
    }
}