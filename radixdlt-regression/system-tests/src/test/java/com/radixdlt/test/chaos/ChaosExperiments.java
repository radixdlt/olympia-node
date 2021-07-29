/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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


    //@Test
    public void stake() throws DeserializeException {
        ECKeyPair richKeyPair = keyPairOf(1);
        AccountAddress richAccount = AccountAddress.create(richKeyPair.getPublicKey());

//        REAddr stakerREAddr = Addressing.ofNetwork(Network.LOCALNET).forAccounts()
//            .parse("ddx1qspj7xtr6av9e2pqcqv4nnenz24g759r7yr3qcxrzftjeneyxkvlktchakswt");
//        AccountAddress staker = AccountAddress.create(stakerREAddr);
        ECKeyPair stakerKeyPair = ECKeyPair.generateNew();
        AccountAddress stakerAddress = AccountAddress.create(stakerKeyPair.getPublicKey());
        System.out.println(stakerAddress);

        ImperativeRadixApi client = ImperativeRadixApi
            .connect("http://localhost", 8080, 3333);

        Amount amount = Amount.ofTokens(1000000);
        FinalizedTransaction finalized = client.transaction().build(TransactionRequest
            .createBuilder(richAccount)
            .transfer(richAccount, stakerAddress, amount.toSubunits(), "xrd_dr1qyrs8qwl")
            .build())
            .toFinalized(richKeyPair);
        TxBlobDTO postFinal = client.transaction().finalize(finalized, false);
        TxDTO response = client.transaction().submit(postFinal);
        System.out.println(response);

        ECPublicKey validatorKey = Addressing.ofNetwork(Network.LOCALNET).forValidators()
            .parse("dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9");
        ValidatorAddress validatorAddress = ValidatorAddress.of(validatorKey);
        Amount amount2 = Amount.ofTokens(10000);
        FinalizedTransaction finalized2 = client.transaction().build(TransactionRequest
            .createBuilder(stakerAddress)
            .stake(stakerAddress, validatorAddress, amount2.toSubunits())
            .build())
            .toFinalized(stakerKeyPair);
        TxBlobDTO postFinal2 = client.transaction().finalize(finalized2, false);
        TxDTO response2 = client.transaction().submit(postFinal2);
        System.out.println(response2);

    }

    @Test
    public void a() throws PrivateKeyException, PublicKeyException, DeserializeException {
        ECKeyPair richKeyPair = keyPairOf(1);
        AccountAddress richAccount = AccountAddress.create(richKeyPair.getPublicKey());

//        REAddr destination = Addressing.ofNetwork(Network.STOKENET).forAccounts()
//            .parse("tdx1qspvkcqggqjftcfjk0te92gvfp42k7qe9myakkeck2hwqt8cv94z9rsy53zh0");
        REAddr destination = Addressing.ofNetwork(Network.STOKENET).forAccounts()
            .parse("tdx1qspllnrh4sx8wxtyl58m872jf2sj2389sywqsxs7cvu8skjprde9r0c63e69n");
        AccountAddress faucetAccount = AccountAddress.create(destination);

        Amount amount = Amount.ofTokens(10000);
        ImperativeRadixApi client = ImperativeRadixApi
            .connect("https://stokenet.radixdlt.com", 443, 443);
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
