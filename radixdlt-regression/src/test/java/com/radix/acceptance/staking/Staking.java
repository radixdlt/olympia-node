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

package com.radix.acceptance.staking;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.Utils;
import com.radix.test.account.Account;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponseDTO;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

public class Staking extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private List<ValidatorDTO> latestValidators = Lists.newArrayList();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        Account account = getTestAccount();
        faucet(account.getAddress());
        Utils.waitForBalance(account, 10);
    }

    @And("I request validator information")
    public void i_request_validator_information() {
        latestValidators.clear();
        latestValidators = getTestAccount().validators(1000, Optional.empty())
                .fold(failure -> new ArrayList<>(), ValidatorsResponseDTO::getValidators);
        if (latestValidators.isEmpty()) {
            Assert.fail("No validators were found in the network, test cannot proceed.");
        }
        logger.info("Found {} validators", latestValidators.size());
    }

    @Then("I observe that validators have stakes delegated to them")
    public void i_observe_that_validators_have_stakes_delegated_to_them() {
        if (latestValidators.isEmpty()) {
            Assert.fail("No validators were found in the network, test cannot proceed.");
        }
        double totalDelegatedStakeAcrossNetwork = latestValidators.stream()
                .mapToDouble(value -> Double.parseDouble(value.getTotalDelegatedStake().toString())).sum();
        assertTrue("No stake was found in any validator, something is probably wrong",
                totalDelegatedStakeAcrossNetwork > 0);
    }

}
