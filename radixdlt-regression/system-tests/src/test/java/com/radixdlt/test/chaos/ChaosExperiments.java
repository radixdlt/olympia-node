/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.test.chaos;

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
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.test.chaos.actions.*;
import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import com.radixdlt.utils.Ints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.Set;

public class ChaosExperiments {

    private static final Logger logger = LogManager.getLogger();

    private final AnsibleImageWrapper ansible = AnsibleImageWrapper.createWithDefaultImage();

    public void pre_release_experiment() {
        //ChaosExperimentUtils.livenessCheckIgnoringOffline(ansible.toNetwork());

        Set<Action> actions = Set.of(
            new NetworkAction(ansible, 0.3),
            new RestartAction(ansible, 0.6),
            new ShutdownAction(ansible, 0.1),
            //new MempoolFillAction(ansible, 0.7, 300), TODO disabled because this brings down the node
            new ValidatorUnregistrationAction(ansible, 0.5)
        );

        actions.forEach(Action::teardown);
        actions.forEach(action -> {
            action.setup();
            ChaosExperimentUtils.waitSeconds(20);
        });

        //ChaosExperimentUtils.livenessCheckIgnoringOffline(ansible.toNetwork());
    }

    @Test
    public void stake() throws DeserializeException {
        ECKeyPair richKeyPair = keyPairOf(2);
        AccountAddress richAccount = AccountAddress.create(richKeyPair.getPublicKey());
        System.out.println("\n" + richAccount.toString(Network.RCNET.getId()) + "\n");

        ImperativeRadixApi client = ImperativeRadixApi
            .connect("https://rcnet.radixdlt.com", 443, 443);
        //52.63.12.179
        ECPublicKey validatorKey = Addressing.ofNetwork(Network.RCNET).forValidators()
            .parse("tv41qfw7gsl80lek83r5j6u6dcx6tyck9v0e9eh6h6nn46rm05p8tg3ycf94ffg");
        ValidatorAddress validatorAddress = ValidatorAddress.of(validatorKey);

        Amount amount = Amount.ofTokens(1000);
        FinalizedTransaction finalized = client.transaction().build(TransactionRequest
            .createBuilder(richAccount)
            .unstake(richAccount, validatorAddress, amount.toSubunits())
            .build())
            .toFinalized(richKeyPair);
        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
        TxDTO response = client.transaction().submit(postFinal);
        System.out.println(response);

    }

   // @Test
    public void a() throws PrivateKeyException, PublicKeyException, DeserializeException {
        ECKeyPair richKeyPair = keyPairOf(1);
        AccountAddress richAccount = AccountAddress.create(richKeyPair.getPublicKey());

        //REAddr faucetREAddr = AccountAddressing.bech32("tdx").parse("tdx1qspjpz3asp8fkq97e2xyvfc7h47wwf78597ssufw75kxrgr7nrdj5ng35dnc3");
        REAddr faucetREAddr = Addressing.ofNetwork(Network.RCNET).forAccounts().parse("tdx1qspn50wwphz8jeu6nnxgv9lmwhf0tw9h9jk0cv2rwp54h5442m757ys3nvc6f");
        AccountAddress faucetAccount = AccountAddress.create(faucetREAddr);

        Amount amount = Amount.ofTokens(10000);
        ImperativeRadixApi client = ImperativeRadixApi
            .connect("https://rcnet.radixdlt.com", 443, 443);
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
