package com.radixdlt.test.system;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Ints;

import static com.radixdlt.client.lib.api.token.Amount.amount;

public class PseudoScripts {

    //@Test
    public void createToken() throws DeserializeException {
        ECKeyPair keyPair = keyPairOf(1);
        AccountAddress myAccount = AccountAddress.create(keyPair.getPublicKey());
        REAddr destination = Addressing.ofNetwork(Network.MAINNET).forAccounts()
            .parse("rdx1qsptsyx03njdw2fzt7y3flrq8wuxzmpq5kmfhcxrm8l86nyjdj9kyyq6n8603");
        AccountAddress destinationAccount = AccountAddress.create(destination);

        ImperativeRadixApi client = ImperativeRadixApi.connect("https://mainnet.radixdlt.com", 443, 443);

//        FinalizedTransaction finalized = client.transaction().build(TransactionRequest.createBuilder(myAccount)
//            .createFixed(myAccount, keyPair.getPublicKey(),
//                "sarscov2",
//                "Coronacoin",
//                "https://en.wikipedia.org/wiki/Severe_acute_respiratory_syndrome_coronavirus_2",
//                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Coronavirus_icon.svg/1024px-Coronavirus_icon.svg.png",
//                "", amount(1000000000).tokens())
//            .build()).toFinalized(keyPair);
//        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
//        TxDTO response = client.transaction().submit(postFinal);
//        System.out.println(response);

        FinalizedTransaction finalized2 = client.transaction().build(TransactionRequest
            .createBuilder(myAccount)
            .transfer(myAccount, destinationAccount, amount(1000000000).tokens(), "sarscov2_rr1q0kpqeff70cf9rtfl5hs25nu8j0srvegpcxtwm5d7z3slzkn8w")
            .build())
            .toFinalized(keyPair);
        TxBlobDTO postFinal2 = client.transaction().finalize(finalized2, false);
        TxDTO response2 = client.transaction().submit(postFinal2);
    }

    //@Test
    public void send() throws DeserializeException {
        ImperativeRadixApi client = ImperativeRadixApi.connect("https://stokenet.radixdlt.com", 443, 443);
        ECKeyPair richKeyPair = keyPairOf(1);
        AccountAddress myAccount = AccountAddress.create(richKeyPair.getPublicKey());
        REAddr destination = Addressing.ofNetwork(Network.STOKENET).forAccounts()
            .parse("tdx1qsptsyx03njdw2fzt7y3flrq8wuxzmpq5kmfhcxrm8l86nyjdj9kyyqmljglj");
        AccountAddress destinationAccount = AccountAddress.create(destination);

        FinalizedTransaction finalized = client.transaction().build(TransactionRequest
            .createBuilder(myAccount)
            .transfer(myAccount, destinationAccount, amount(1000000000).tokens(), "slri_tr1qw2p5tsguva7wntrvufjcva8j9atqjhja06u6xqn4uxqk3nf3s")
            .build())
            .toFinalized(richKeyPair);
        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
        TxDTO response = client.transaction().submit(postFinal);
        System.out.println(response);
    }

    //@Test
    public void stakeUnstake() throws DeserializeException {
        ECKeyPair richKeyPair = keyPairOf(1);
        AccountAddress myAccount = AccountAddress.create(richKeyPair.getPublicKey());

        REAddr destination = Addressing.ofNetwork(Network.STOKENET).forAccounts()
            .parse("tdx1qspva9mlwhe5vhz60hmkknfyfs9e5dqfh7psxckxza80qsy7slg2pqqtlqjcq");
        AccountAddress faucetAccount = AccountAddress.create(destination);

        ImperativeRadixApi client = ImperativeRadixApi.connect("https://stokenet.radixdlt.com", 443, 443);

        System.out.println(client.account().balances(myAccount).getTokenBalances());

//        FinalizedTransaction finalized = client.transaction().build(TransactionRequest
//            .createBuilder(myAccount)
//            .transfer(myAccount, faucetAccount, amount.toSubunits(), "xrd_tr1qyf0x76s")
//            .build())
//            .toFinalized(richKeyPair);
//        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
//        TxDTO response = client.transaction().submit(postFinal);

        ECPublicKey validatorKey = Addressing.ofNetworkId(2).forValidators().fromString("tv1q0r64m7xfgg9t3d0dtcs8krcdj73fcqvkmup2w0wpywt36tteps0slpyra8").fold(error -> null, a -> a);
        ValidatorAddress validatorAddress = ValidatorAddress.of(validatorKey);

        Amount amount = Amount.ofTokens(50);

//        FinalizedTransaction finalized = client.transaction().build(TransactionRequest
//            .createBuilder(myAccount)
//            .stake(myAccount, validatorAddress, amount.toSubunits())
//            .build())
//            .toFinalized(richKeyPair);
//        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
//        TxDTO response = client.transaction().submit(postFinal);
//        System.out.println(response);
        //79293678619524d2cf1cf5454dde7b37ddae5b6a80989385949bbac938b9444a

        FinalizedTransaction finalized2 = client.transaction().build(TransactionRequest
            .createBuilder(myAccount)
            .unstake(myAccount, validatorAddress, amount.toSubunits())
            .build())
            .toFinalized(richKeyPair);
        TxBlobDTO postFinal2 = client.transaction().finalize(finalized2, false);
        TxDTO response2 = client.transaction().submit(postFinal2);
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
