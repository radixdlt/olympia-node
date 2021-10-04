package com.radixdlt.test.account;

import com.radixdlt.api.rpc.dto.TransactionDTO;
import com.radixdlt.api.types.ValidatorAddress;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.identifiers.AID;

import java.util.Optional;

public interface RadixAccount {

    TransactionDTO lookup(AID txID);

    AID transfer(Account destination, Amount amount, Optional<String> message);

    AID stake(ValidatorAddress validatorAddress, Amount amount);

    AID fixedSupplyToken(String symbol, String name, String description, String iconUrl, String tokenUrl, Amount supply);

}
