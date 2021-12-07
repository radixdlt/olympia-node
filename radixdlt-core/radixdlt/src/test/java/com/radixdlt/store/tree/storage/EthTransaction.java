package com.radixdlt.store.tree.storage;

import com.radixdlt.store.tree.serialization.rlp.RLP;

import java.math.BigInteger;
import java.util.Arrays;

public record EthTransaction(
        BigInteger accountNonce,
        BigInteger gasPrice,
        BigInteger gasLimit,
        byte[] recipient,
        BigInteger amount,
        byte[] payload,
        byte[] v,
        byte[] r,
        byte[] s
) {
    public byte[] rlpEncoded() {
        return RLP.encodeList(
                RLP.encodeBigInteger(accountNonce),
                RLP.encodeBigInteger(gasPrice),
                RLP.encodeBigInteger(gasLimit),
                RLP.encodeElement(recipient),
                RLP.encodeBigInteger(amount),
                RLP.encodeElement(payload),
                RLP.encodeElement(v),
                RLP.encodeElement(r),
                RLP.encodeElement(s)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EthTransaction that = (EthTransaction) o;

        if (!accountNonce.equals(that.accountNonce)) {
            return false;
        }
        if (!gasPrice.equals(that.gasPrice)) {
            return false;
        }
        if (!gasLimit.equals(that.gasLimit)) {
            return false;
        }
        if (!Arrays.equals(recipient, that.recipient)) {
            return false;
        }
        if (!amount.equals(that.amount)) {
            return false;
        }
        if (!Arrays.equals(payload, that.payload)) {
            return false;
        }
        if (!Arrays.equals(v, that.v)) {
            return false;
        }
        if (!Arrays.equals(r, that.r)) {
            return false;
        }
        return Arrays.equals(s, that.s);
    }

    @Override
    public int hashCode() {
        int result = accountNonce.hashCode();
        result = 31 * result + gasPrice.hashCode();
        result = 31 * result + gasLimit.hashCode();
        result = 31 * result + Arrays.hashCode(recipient);
        result = 31 * result + amount.hashCode();
        result = 31 * result + Arrays.hashCode(payload);
        result = 31 * result + Arrays.hashCode(v);
        result = 31 * result + Arrays.hashCode(r);
        result = 31 * result + Arrays.hashCode(s);
        return result;
    }
}
