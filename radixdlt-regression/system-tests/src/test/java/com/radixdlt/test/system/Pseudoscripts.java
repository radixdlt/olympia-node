package com.radixdlt.test.system;


import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Ints;

public class Pseudoscripts {

    //@Test
    public void sendTokens() throws DeserializeException {

        ECKeyPair richKeyPair = keyPairOf(1);
        AccountAddress richAccount = AccountAddress.create(richKeyPair.getPublicKey());
        REAddr destination = Addressing.ofNetwork(Network.STOKENET).forAccounts()
            .parse("tdx1qspva9mlwhe5vhz60hmkknfyfs9e5dqfh7psxckxza80qsy7slg2pqqtlqjcq");
        AccountAddress faucetAccount = AccountAddress.create(destination);

        Amount amount = Amount.ofTokens(10000);
        ImperativeRadixApi client = ImperativeRadixApi
            .connect("https://stokenet.radixdlt.com", 443, 443);

        System.out.println(client.account().balances(richAccount).getTokenBalances());

        FinalizedTransaction finalized = client.transaction().build(TransactionRequest
            .createBuilder(richAccount)
            .transfer(richAccount, faucetAccount, amount.toSubunits(), "xrd_tr1qyf0x76s")
            .build())
            .toFinalized(richKeyPair);
        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
        TxDTO response = client.transaction().submit(postFinal);

        System.out.println(response);
    }

    private static ECKeyPair keyPairOf(int pk) {
        var privateKey = new byte[ECKeyPair.BYTES];
        Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

        try {
            return ECKeyPair.fromPrivateKey(privateKey);
        } catch (PrivateKeyException | PublicKeyException e) {
            throw new IllegalArgumentException("Error while generating public key", e);
        }
    }


}
