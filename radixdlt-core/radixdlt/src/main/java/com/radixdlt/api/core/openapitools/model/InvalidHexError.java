/*
 * Radix Core API
 * This API provides endpoints from a node for integration with the Radix ledger.  # Overview  > WARNING > > The Core API is __NOT__ intended to be available on the public web. It is > designed to be accessed in a private network.  The Core API is separated into three: * The **Data API** is a read-only api which allows you to view and sync to the state of the ledger. * The **Construction API** allows you to construct and submit a transaction to the network. * The **Key API** allows you to use the keys managed by the node to sign transactions.  The Core API is a low level API primarily designed for network integrations such as exchanges, ledger analytics providers, or hosted ledger data dashboards where detailed ledger data is required and the integrator can be expected to run their node to provide the Core API for their own consumption.  For a higher level API, see the [Gateway API](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/radixdlt/radixdlt-network-gateway/main/generation/gateway-api-spec.yaml).  For node monitoring, see the [System API](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/radixdlt/radixdlt/main/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/system/api.yaml).  ## Rosetta  The Data API and Construction API is inspired from [Rosetta API](https://www.rosetta-api.org/) most notably:   * Use of a JSON-Based RPC protocol on top of HTTP Post requests   * Use of Operations, Amounts, and Identifiers as universal language to   express asset movement for reading and writing  There are a few notable exceptions to note:   * Fetching of ledger data is through a Transaction stream rather than a   Block stream   * Use of `EntityIdentifier` rather than `AccountIdentifier`   * Use of `OperationGroup` rather than `related_operations` to express related   operations   * Construction endpoints perform coin selection on behalf of the caller.   This has the unfortunate effect of not being able to support high frequency   transactions from a single account. This will be addressed in future updates.   * Construction endpoints are online rather than offline as required by Rosetta  Future versions of the api will aim towards a fully-compliant Rosetta API.  ## Enabling Endpoints  All endpoints are enabled when running a node with the exception of two endpoints, each of which need to be manually configured to access: * `/transactions` endpoint must be enabled with configuration `api.transaction.enable=true`. This is because the transactions endpoint requires additional database storage which may not be needed for users who aren't using this endpoint * `/key/sign` endpoint must be enable with configuration `api.sign.enable=true`. This is a potentially dangerous endpoint if accessible publicly so it must be enabled manually.  ## Client Code Generation  We have found success with generating clients against the [api.yaml specification](https://raw.githubusercontent.com/radixdlt/radixdlt/main/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/api.yaml). See https://openapi-generator.tech/ for more details.  The OpenAPI generator only supports openapi version 3.0.0 at present, but you can change 3.1.0 to 3.0.0 in the first line of the spec without affecting generation.  # Data API Flow  The Data API can be used to synchronize a full or partial view of the ledger, transaction by transaction.  ![Data API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/update-documentation/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/data_sequence_flow.png)  # Construction API Flow  The Construction API can be used to construct and submit transactions to the network.  ![Construction API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/documentation/construction_sequence_flow.png)  Unlike the Rosetta Construction API [specification](https://www.rosetta-api.org/docs/construction_api_introduction.html), this Construction API selects UTXOs on behalf of the caller. This has the unfortunate side effect of not being able to support high frequency transactions from a single account due to UTXO conflicts. This will be addressed in a future release. 
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.radixdlt.api.core.openapitools.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.radixdlt.api.core.openapitools.JSON;
import io.swagger.annotations.ApiModelProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
/**
 * InvalidHexError
 */
@JsonPropertyOrder({
  InvalidHexError.JSON_PROPERTY_INVALID_HEX
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2022-07-06T21:16:58.881714945+02:00[Europe/Warsaw]")

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AboveMaximumValidatorFeeIncreaseError.class, name = "AboveMaximumValidatorFeeIncreaseError"),
  @JsonSubTypes.Type(value = BelowMinimumStakeError.class, name = "BelowMinimumStakeError"),
  @JsonSubTypes.Type(value = DataObjectNotSupportedByEntityError.class, name = "DataObjectNotSupportedByEntityError"),
  @JsonSubTypes.Type(value = EngineIsShutDownError.class, name = "EngineIsShutDownError"),
  @JsonSubTypes.Type(value = FeeConstructionError.class, name = "FeeConstructionError"),
  @JsonSubTypes.Type(value = InternalServerError.class, name = "InternalServerError"),
  @JsonSubTypes.Type(value = InvalidAddressError.class, name = "InvalidAddressError"),
  @JsonSubTypes.Type(value = InvalidDataObjectError.class, name = "InvalidDataObjectError"),
  @JsonSubTypes.Type(value = InvalidFeePayerEntityError.class, name = "InvalidFeePayerEntityError"),
  @JsonSubTypes.Type(value = InvalidHexError.class, name = "InvalidHexError"),
  @JsonSubTypes.Type(value = InvalidJsonError.class, name = "InvalidJsonError"),
  @JsonSubTypes.Type(value = InvalidPartialStateIdentifierError.class, name = "InvalidPartialStateIdentifierError"),
  @JsonSubTypes.Type(value = InvalidPublicKeyError.class, name = "InvalidPublicKeyError"),
  @JsonSubTypes.Type(value = InvalidSignatureError.class, name = "InvalidSignatureError"),
  @JsonSubTypes.Type(value = InvalidSubEntityError.class, name = "InvalidSubEntityError"),
  @JsonSubTypes.Type(value = InvalidTransactionError.class, name = "InvalidTransactionError"),
  @JsonSubTypes.Type(value = InvalidTransactionHashError.class, name = "InvalidTransactionHashError"),
  @JsonSubTypes.Type(value = MempoolFullError.class, name = "MempoolFullError"),
  @JsonSubTypes.Type(value = MessageTooLongError.class, name = "MessageTooLongError"),
  @JsonSubTypes.Type(value = NetworkNotSupportedError.class, name = "NetworkNotSupportedError"),
  @JsonSubTypes.Type(value = NotEnoughNativeTokensForFeesError.class, name = "NotEnoughNativeTokensForFeesError"),
  @JsonSubTypes.Type(value = NotEnoughResourcesError.class, name = "NotEnoughResourcesError"),
  @JsonSubTypes.Type(value = NotValidatorEntityError.class, name = "NotValidatorEntityError"),
  @JsonSubTypes.Type(value = NotValidatorOwnerError.class, name = "NotValidatorOwnerError"),
  @JsonSubTypes.Type(value = PublicKeyNotSupportedError.class, name = "PublicKeyNotSupportedError"),
  @JsonSubTypes.Type(value = ResourceDepositOperationNotSupportedByEntityError.class, name = "ResourceDepositOperationNotSupportedByEntityError"),
  @JsonSubTypes.Type(value = ResourceWithdrawOperationNotSupportedByEntityError.class, name = "ResourceWithdrawOperationNotSupportedByEntityError"),
  @JsonSubTypes.Type(value = StateIdentifierNotFoundError.class, name = "StateIdentifierNotFoundError"),
  @JsonSubTypes.Type(value = SubstateDependencyNotFoundError.class, name = "SubstateDependencyNotFoundError"),
  @JsonSubTypes.Type(value = TransactionNotFoundError.class, name = "TransactionNotFoundError"),
})

public class InvalidHexError extends CoreError {
  public static final String JSON_PROPERTY_INVALID_HEX = "invalid_hex";
  private String invalidHex;

  public InvalidHexError() { 
  }

  public InvalidHexError invalidHex(String invalidHex) {
    this.invalidHex = invalidHex;
    return this;
  }

   /**
   * Get invalidHex
   * @return invalidHex
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_INVALID_HEX)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getInvalidHex() {
    return invalidHex;
  }


  @JsonProperty(JSON_PROPERTY_INVALID_HEX)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setInvalidHex(String invalidHex) {
    this.invalidHex = invalidHex;
  }


  /**
   * Return true if this InvalidHexError object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InvalidHexError invalidHexError = (InvalidHexError) o;
    return Objects.equals(this.invalidHex, invalidHexError.invalidHex) &&
        super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(invalidHex, super.hashCode());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InvalidHexError {\n");
    sb.append("    ").append(toIndentedString(super.toString())).append("\n");
    sb.append("    invalidHex: ").append(toIndentedString(invalidHex)).append("\n");
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
  mappings.put("AboveMaximumValidatorFeeIncreaseError", AboveMaximumValidatorFeeIncreaseError.class);
  mappings.put("BelowMinimumStakeError", BelowMinimumStakeError.class);
  mappings.put("DataObjectNotSupportedByEntityError", DataObjectNotSupportedByEntityError.class);
  mappings.put("EngineIsShutDownError", EngineIsShutDownError.class);
  mappings.put("FeeConstructionError", FeeConstructionError.class);
  mappings.put("InternalServerError", InternalServerError.class);
  mappings.put("InvalidAddressError", InvalidAddressError.class);
  mappings.put("InvalidDataObjectError", InvalidDataObjectError.class);
  mappings.put("InvalidFeePayerEntityError", InvalidFeePayerEntityError.class);
  mappings.put("InvalidHexError", InvalidHexError.class);
  mappings.put("InvalidJsonError", InvalidJsonError.class);
  mappings.put("InvalidPartialStateIdentifierError", InvalidPartialStateIdentifierError.class);
  mappings.put("InvalidPublicKeyError", InvalidPublicKeyError.class);
  mappings.put("InvalidSignatureError", InvalidSignatureError.class);
  mappings.put("InvalidSubEntityError", InvalidSubEntityError.class);
  mappings.put("InvalidTransactionError", InvalidTransactionError.class);
  mappings.put("InvalidTransactionHashError", InvalidTransactionHashError.class);
  mappings.put("MempoolFullError", MempoolFullError.class);
  mappings.put("MessageTooLongError", MessageTooLongError.class);
  mappings.put("NetworkNotSupportedError", NetworkNotSupportedError.class);
  mappings.put("NotEnoughNativeTokensForFeesError", NotEnoughNativeTokensForFeesError.class);
  mappings.put("NotEnoughResourcesError", NotEnoughResourcesError.class);
  mappings.put("NotValidatorEntityError", NotValidatorEntityError.class);
  mappings.put("NotValidatorOwnerError", NotValidatorOwnerError.class);
  mappings.put("PublicKeyNotSupportedError", PublicKeyNotSupportedError.class);
  mappings.put("ResourceDepositOperationNotSupportedByEntityError", ResourceDepositOperationNotSupportedByEntityError.class);
  mappings.put("ResourceWithdrawOperationNotSupportedByEntityError", ResourceWithdrawOperationNotSupportedByEntityError.class);
  mappings.put("StateIdentifierNotFoundError", StateIdentifierNotFoundError.class);
  mappings.put("SubstateDependencyNotFoundError", SubstateDependencyNotFoundError.class);
  mappings.put("TransactionNotFoundError", TransactionNotFoundError.class);
  mappings.put("InvalidHexError", InvalidHexError.class);
  JSON.registerDiscriminator(InvalidHexError.class, "type", mappings);
}
}

