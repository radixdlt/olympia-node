/*
 * Radix Gateway API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 0.9.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.radixdlt.api.gateway.openapitools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.radixdlt.api.gateway.openapitools.JSON;
import io.swagger.annotations.ApiModelProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AccountBalancesResponseSuccess
 */
@JsonPropertyOrder({
  AccountBalancesResponseSuccess.JSON_PROPERTY_LEDGER_STATE,
  AccountBalancesResponseSuccess.JSON_PROPERTY_ACCOUNT_BALANCES
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-12-01T18:03:23.286227-06:00[America/Chicago]")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AccountBalancesResponseError.class, name = "AccountBalancesResponseError"),
  @JsonSubTypes.Type(value = AccountBalancesResponse.class, name = "AccountBalancesResponseSuccess"),
})

public class AccountBalancesResponseSuccess extends AccountBalancesResponse {
  public static final String JSON_PROPERTY_LEDGER_STATE = "ledger_state";
  private LedgerState ledgerState;

  public static final String JSON_PROPERTY_ACCOUNT_BALANCES = "account_balances";
  private AccountBalances accountBalances;


  public AccountBalancesResponseSuccess ledgerState(LedgerState ledgerState) {
    this.ledgerState = ledgerState;
    return this;
  }

   /**
   * Get ledgerState
   * @return ledgerState
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_LEDGER_STATE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public LedgerState getLedgerState() {
    return ledgerState;
  }


  @JsonProperty(JSON_PROPERTY_LEDGER_STATE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setLedgerState(LedgerState ledgerState) {
    this.ledgerState = ledgerState;
  }


  public AccountBalancesResponseSuccess accountBalances(AccountBalances accountBalances) {
    this.accountBalances = accountBalances;
    return this;
  }

   /**
   * Get accountBalances
   * @return accountBalances
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_ACCOUNT_BALANCES)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public AccountBalances getAccountBalances() {
    return accountBalances;
  }


  @JsonProperty(JSON_PROPERTY_ACCOUNT_BALANCES)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setAccountBalances(AccountBalances accountBalances) {
    this.accountBalances = accountBalances;
  }


  /**
   * Return true if this AccountBalancesResponseSuccess object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AccountBalancesResponseSuccess accountBalancesResponseSuccess = (AccountBalancesResponseSuccess) o;
    return Objects.equals(this.ledgerState, accountBalancesResponseSuccess.ledgerState) &&
        Objects.equals(this.accountBalances, accountBalancesResponseSuccess.accountBalances) &&
        super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ledgerState, accountBalances, super.hashCode());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AccountBalancesResponseSuccess {\n");
    sb.append("    ").append(toIndentedString(super.toString())).append("\n");
    sb.append("    ledgerState: ").append(toIndentedString(ledgerState)).append("\n");
    sb.append("    accountBalances: ").append(toIndentedString(accountBalances)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

static {
  // Initialize and register the discriminator mappings.
  Map<String, Class<?>> mappings = new HashMap<String, Class<?>>();
  mappings.put("AccountBalancesResponseError", AccountBalancesResponseError.class);
  mappings.put("AccountBalancesResponseSuccess", AccountBalancesResponse.class);
  mappings.put("AccountBalancesResponseSuccess", AccountBalancesResponseSuccess.class);
  JSON.registerDiscriminator(AccountBalancesResponseSuccess.class, "type", mappings);
}
}
