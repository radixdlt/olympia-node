package com.radixdlt.store.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radixdlt.store.tree.hash.Keccak256;
import com.radixdlt.store.tree.serialization.rlp.RLP;
import com.radixdlt.store.tree.serialization.rlp.RLPSerializer;
import com.radixdlt.store.tree.storage.EthTransaction;
import com.radixdlt.store.tree.storage.InMemoryPMTStorage;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;

public class EthereumTxTreeTest {

    private static final Keccak256 KECCAK_256 = new Keccak256();
    private static final RLPSerializer RLP_SERIALIZER = new RLPSerializer();

    @Test
    public void when_tx_tree_of_eth_block_10593417_created_using_cache__then_tx_root_is_correct() {
        var storage = new InMemoryPMTStorage();
        var tree = new PMT(storage, KECCAK_256, RLP_SERIALIZER, Duration.of(10, ChronoUnit.MINUTES));

        createEthereumTxTreeBlock10593417Test(tree);
    }

    @Test
    public void when_tx_tree_of_eth_block_10593417_created_not_using_cache__then_tx_root_is_correct() {
        var storage = new InMemoryPMTStorage();
        var tree = new PMT(storage, KECCAK_256, RLP_SERIALIZER, Duration.ZERO);

        createEthereumTxTreeBlock10593417Test(tree);
    }

    private void createEthereumTxTreeBlock10593417Test(PMT tree) {
        tree.add(
                Hex.decode("80"),
                Hex.decode("f8ab81a5852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb00"
                        + "00000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000"
                        + "000000000000000056bc75e2d6310000026a06c89b57113cf7da8aed7911310e03d49be5e40de0bd73af4c9c54726"
                        + "c478691ba056223f039fab98d47c71f84190cf285ce8fc7d9181d6769387e5efd0a970e2e9")
        );

        tree.add(
                Hex.decode("01"),
                Hex.decode("f8ab81a6852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb000"
                        + "0000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e2500000000000000000000000000000000"
                        + "00000000000000056bc75e2d6310000026a0d77c66153a661ecc986611dffda129e14528435ed3fd244c3afb0d434"
                        + "e9fd1c1a05ab202908bf6cbc9f57c595e6ef3229bce80a15cdf67487873e57cc7f5ad7c8a")
        );

        tree.add(
                Hex.decode("02"),
                Hex.decode("f86d8229f185199c82cc008252089488e9a2d38e66057e18545ce03b3ae9ce4fc360538702ce7de1537c008"
                        + "025a096e7a1d9683b205f697b4073a3e2f0d0ad42e708f03e899c61ed6a894a7f916aa05da238fbb96d41a4b5ec03"
                        + "38c86cfcb627d0aa8e556f21528e62f31c32f7e672")
        );

        byte[] rootHash = tree.add(
                Hex.decode("03"),
                Hex.decode("f86f826b2585199c82cc0083015f9094e955ede0a3dbf651e2891356ecd0509c1edb8d9c8801051fdc4efdc"
                        + "0008025a02190f26e70a82d7f66354a13cda79b6af1aa808db768a787aeb348d425d7d0b3a06a82bd0518bc9b69dc"
                        + "551e20d772a1b06222edfc5d39b6973e4f4dc46ed8b196")
        );

        Assert.assertEquals(
                "ab41f886be23cd786d8a69a72b0f988ea72e0b2e03970d0798f5e03763a442cc",
                Hex.toHexString(rootHash)
        );
    }

    @Test
    public void when_tx_tree_of_eth_block_10467135_created_using_cache__then_tx_root_is_correct() throws IOException {
        var storage = new InMemoryPMTStorage();
        var tree = new PMT(storage, KECCAK_256, RLP_SERIALIZER, Duration.of(10, ChronoUnit.MINUTES));

        createEthereumTxTreeBlock10467135Test(tree);
    }

    @Test
    public void when_tx_tree_of_eth_block_10467135_created_not_using_cache__then_tx_root_is_correct() throws IOException {
        var storage = new InMemoryPMTStorage();
        var tree = new PMT(storage, KECCAK_256, RLP_SERIALIZER, Duration.of(0, ChronoUnit.MINUTES));

        createEthereumTxTreeBlock10467135Test(tree);
    }

    private void createEthereumTxTreeBlock10467135Test(PMT tree) throws IOException {
        String expectedTxRootHash = "bb345e208bda953c908027a45aa443d6cab6b8d2fd64e83ec52f1008ddeafa58";

        Map<String, String>[] maps = new ObjectMapper().readValue(
                Paths.get("src/test/resources", "eth-txs/eth-txs.json").toFile(), Map[].class);

        var txRLPEncodedList = new ArrayList<byte[]>();

        byte[] rootHash = new byte[0];

        for (int i = 0; i < maps.length; i++) {
            Map<String, String> m = maps[i];

            EthTransaction ethTransaction = getEthTransaction(m);
            byte[] txRLPEncoded = ethTransaction.rlpEncoded();
            txRLPEncodedList.add(txRLPEncoded);

            Assert.assertArrayEquals(
                    decodeHex(m.get("hash")),
                    KECCAK_256.hash(txRLPEncoded));

            byte[] txIdxRLPEncoded = RLP.encodeBigInteger(BigInteger.valueOf(i));

            rootHash = tree.add(
                    txIdxRLPEncoded,
                    txRLPEncoded
            );

            Assert.assertArrayEquals(
                    txRLPEncoded,
                    tree.get(txIdxRLPEncoded)
            );
        }

        for (int i = 0; i < txRLPEncodedList.size(); i++) {
            Assert.assertArrayEquals(
                    txRLPEncodedList.get(i),
                    tree.get(RLP.encodeBigInteger(BigInteger.valueOf(i)))
            );
        }

        Assert.assertEquals(
                expectedTxRootHash,
                Hex.toHexString(rootHash)
        );
    }

    private EthTransaction getEthTransaction(Map<String, String> m) {
        EthTransaction ethTransaction = new EthTransaction(
                new BigInteger(decodeHex(m.get("nonce"))),
                new BigInteger(decodeHex(m.get("gasPrice"))),
                new BigInteger(decodeHex(m.get("gas"))),
                decodeHex(m.get("to")),
                new BigInteger(decodeHex(m.get("value"))),
                decodeHex(m.get("input")),
                decodeHex(m.get("v")),
                decodeHex(m.get("r")),
                decodeHex(m.get("s"))
        );
        return ethTransaction;
    }


    private byte[] decodeHex(String hexString) {
        String finalHexString;
        finalHexString = hexString.substring(2); // removing 0x
        if (finalHexString.length() % 2 != 0) {
            finalHexString = "0" + finalHexString; // add a zero if odd length
        }
        return Hex.decode(finalHexString);
    }
}