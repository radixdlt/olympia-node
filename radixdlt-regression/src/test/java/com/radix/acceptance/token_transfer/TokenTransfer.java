package com.radix.acceptance.token_transfer;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.Utils;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import io.cucumber.java.en.Given;

import static org.junit.Assert.*;

public class TokenTransfer extends AcceptanceTest {

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        getTestAccount().onSuccess(account -> faucet(account.getAddress()));
        Utils.waitForBalance(getTestAccount(), 10L);
    }

    @Given("I can transfer {int} XRD to another account")
    public void i_can_transfer_xrd_to_another_account(Integer int1) {
        getTestAccount().onSuccess(account -> {
            TransactionRequest request = TransactionRequest.createBuilder().transfer(
                    account.getAddress(),
                    AccountAddress.create(ECKeyPair.generateNew().getPublicKey()),
                    UInt256.from(5),
                    "rri").build();



//            prepareClient(BUILT_TRANSACTION)
//                    .onFailure(failure -> fail(failure.toString()))
//                    .onSuccess(client -> client.buildTransaction(request)
//                            .onFailure(failure -> fail(failure.toString()))
//                            .onSuccess(dto -> assertEquals(UInt256.from(100000000000000000L), dto.getFee()))
//                            .onSuccess(dto -> assertArrayEquals(hash, dto.getTransaction().getHashToSign()))
//                    );

//            account.buildTransaction(request).onSuccess(builtTransactionDTO ->
//                    account.submitTransaction(builtTransactionDTO.toFinalized(account.getKeyPair()))
//                            .onSuccess(txDTO -> builtTransactionDTO.toFinalized(account.getKeyPair())
//                            );
        });
    }

    @Given("That account can transfer {int} XRD back to me")
    public void i_that_account_can_transfer_xrd_back_to_me(Integer int1) {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }

}
