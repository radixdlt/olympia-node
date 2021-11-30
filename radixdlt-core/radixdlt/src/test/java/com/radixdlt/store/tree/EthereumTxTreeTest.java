package com.radixdlt.store.tree;

import com.radixdlt.store.tree.PMT;
import com.radixdlt.store.tree.PMTCachedStorage;
import com.radixdlt.store.tree.serialization.rlp.RLP;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.crypto.digests.SHA3Digest;

public class EthereumTxTreeTest {

    @Test
    public void t() {
        var storage = new PMTCachedStorage();
        var tree = new PMT(storage);

        System.out.println(Hex.toHexString(sha3(RLP.encodeElement(new byte[0]))));

        tree.add(
                Hex.decode("80"),
                Hex.decode("f8ab81a5852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb0000000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000000000000000000056bc75e2d6310000026a06c89b57113cf7da8aed7911310e03d49be5e40de0bd73af4c9c54726c478691ba056223f039fab98d47c71f84190cf285ce8fc7d9181d6769387e5efd0a970e2e9")
        );

        tree.add(
                Hex.decode("01"),
                Hex.decode("f8ab81a6852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb0000000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000000000000000000056bc75e2d6310000026a0d77c66153a661ecc986611dffda129e14528435ed3fd244c3afb0d434e9fd1c1a05ab202908bf6cbc9f57c595e6ef3229bce80a15cdf67487873e57cc7f5ad7c8a")
        );

        tree.add(
                Hex.decode("02"),
                Hex.decode("f86d8229f185199c82cc008252089488e9a2d38e66057e18545ce03b3ae9ce4fc360538702ce7de1537c008025a096e7a1d9683b205f697b4073a3e2f0d0ad42e708f03e899c61ed6a894a7f916aa05da238fbb96d41a4b5ec0338c86cfcb627d0aa8e556f21528e62f31c32f7e672")
        );

        byte[] rootHash = tree.add(
                Hex.decode("03"),
                Hex.decode("f86f826b2585199c82cc0083015f9094e955ede0a3dbf651e2891356ecd0509c1edb8d9c8801051fdc4efdc0008025a02190f26e70a82d7f66354a13cda79b6af1aa808db768a787aeb348d425d7d0b3a06a82bd0518bc9b69dc551e20d772a1b06222edfc5d39b6973e4f4dc46ed8b196")
        );

        Assert.assertEquals(
                "ab41f886be23cd786d8a69a72b0f988ea72e0b2e03970d0798f5e03763a442cc",
                Hex.toHexString(rootHash)
        );
    }

    @Test
    public void t2() {
        var storage = new PMTCachedStorage();
        var tree = new PMT(storage);

        tree.add(
                Hex.decode("80"),
                Hex.decode("f9010c820144853fcf6e43c5830493e094897c3dec007e1bcd7b8dcc1f304c2246eea6853780b8a46b038dca0000000000000000000000004f2604aac91114ae3b3d0be485d407d02b24480b00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000147d35700000000000000000000000000000000000000000000000000000000003b9ac9ff0000000000000000000000000000000000000000000000000000000000b5bc4d26a0d6537ab8b4f5161b07a53265b1fb7f73d84745911e6eb9ca11613a26ccf0c2f4a055b26eb0b1530a0da9ea1a29a322e2b6db0e374b313a0be397a598bda48e73b3")
        );

        byte[] rootHash = tree.add(
                Hex.decode("01"),
                Hex.decode("f8a91c85126a21a08082a10594dac17f958d2ee523a2206206994597c13d831ec780b844a9059cbb000000000000000000000000cb9e24937d393373790a1e31af300e05a501d40c00000000000000000000000000000000000000000000000000000006a79eb58025a017ba02f156b099df4a73b1f2183943dc23d5deb24e0a50fb5ea33b90bff5f6cba06cff7c6c51c50b50c2e727aa31729e261ea9b92672a69863a89fe72f1968262a")
        );

        Assert.assertEquals(
                "88796e4f9cfeca7b53f666e3103a1ba981b9445b78bf687788e1ad8976843d83",
                Hex.toHexString(rootHash)
        );
    }

    private byte[] sha3(byte[] data) {
        SHA3Digest sha3Digest = new SHA3Digest(256);
        byte[] hashed = new byte[sha3Digest.getDigestSize()];
        if (data.length != 0) {
            sha3Digest.update(data, 0, data.length);
        }
        sha3Digest.doFinal(hashed, 0);
        return hashed;
    }
}
