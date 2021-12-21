package com.radixdlt.tree.serialization.rlp;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class RLPEthereumTxTest {

    @Test
    public void third_tx_of_block_10593417_is_RLP_encoded_correctly() {
        //given

        // Result of https://etherscan.io/getRawTx?tx=0x0b41fc4c1d8518cdeda9812269477256bdc415eb39c4531885ff9728d6ad096b
        final var expectedTxHexString = "f86f826b2585199c82cc0083015f9094e955ede0a3dbf651e2891356ecd0509c1edb8d9c88010"
                + "51fdc4efdc0008025a02190f26e70a82d7f66354a13cda79b6af1aa808db768a787aeb348d425d7d0b3a06a82bd0518bc9b69d"
                + "c551e20d772a1b06222edfc5d39b6973e4f4dc46ed8b196";

        // This is the Hex encoded data of the last tx of block 10593417, tx hash: 0x0b41fc4c1d8518cdeda9812269477256bdc415eb39c4531885ff9728d6ad096b
        // We can get it running: curl https://cloudflare-eth.com -H 'Content-Type: application/json'
        // --data '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params": ["0xa1a489", true],"id":1}'
        String nonce = "6b25";
        String gasPrice = "199c82cc00";
        String gasLimit = "015f90";
        String recipient = "e955ede0a3dbf651e2891356ecd0509c1edb8d9c";
        String value = "01051fdc4efdc000";
        byte[] data = "".getBytes(StandardCharsets.UTF_8);
        String v = "25";
        String r = "2190f26e70a82d7f66354a13cda79b6af1aa808db768a787aeb348d425d7d0b3";
        String s = "6a82bd0518bc9b69dc551e20d772a1b06222edfc5d39b6973e4f4dc46ed8b196";

        // when
        byte[] txRLPEncoded = RLP.encodeList(
                RLP.encodeElement(Hex.decode(nonce)),
                RLP.encodeElement(Hex.decode(gasPrice)),
                RLP.encodeElement(Hex.decode(gasLimit)),
                RLP.encodeElement(Hex.decode(recipient)),
                RLP.encodeElement(Hex.decode(value)),
                RLP.encodeElement(data),
                RLP.encodeElement(Hex.decode(v)),
                RLP.encodeElement(Hex.decode(r)),
                RLP.encodeElement(Hex.decode(s))
        );

        String actualTxHexString = Hex.toHexString(txRLPEncoded);

        //then
        Assert.assertEquals(
                expectedTxHexString,
                actualTxHexString
        );
    }
}
