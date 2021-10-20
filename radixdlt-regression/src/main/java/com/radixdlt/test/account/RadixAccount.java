package com.radixdlt.test.account;

import com.radixdlt.api.addressing.ValidatorAddress;
import com.radixdlt.api.dto.response.TransactionDTO;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.identifiers.AID;

import java.util.Optional;

public interface RadixAccount {

    TransactionDTO lookup(AID txID);

    AID transfer(Account destination, Amount amount, Optional<String> message);

    AID stake(ValidatorAddress validatorAddress, Amount amount);

    AID fixedSupplyToken(String symbol, String name, String description, String iconUrl, String tokenUrl, Amount supply);

}
