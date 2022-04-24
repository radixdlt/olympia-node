/*
 * Radix Core API
 * This API provides endpoints for Radix network integrators.  # Overview  > WARNING > > The Core API is __NOT__ intended to be available on the public web. It is > mainly designed to be accessed in a private network for integration use.  Welcome to the Radix Core API version 0.9.0 for Integrators. Version 0.9.0 is intended for integrators who wish to begin the process of developing an integration between the Radix ledger and their own systems.  The Core API is separated into two: * The **Data API** is a read-only api which allows integrators to view and sync to the state of the ledger. * The **Construction API** allows integrators to construct and submit a transaction to the network on behalf of a key holder.  The Core API is primarily designed for network integrations such as exchanges, ledger analytics providers, or hosted ledger data dashboards where detailed ledger data is required and the integrator can be expected to run their node to provide the Core API for their own consumption.  The Core API is not a full replacement for the current Node and Archive [APIs](https://docs.radixdlt.com). We are also working on a public-facing Gateway API that will be part of a full \"new API\", but is yet to be finalised.  We should stress that this API is in preview, and should __not__ be deployed into production until version 1.0.0 has been finalised in an official Radix node release.  ## Backwards Compatibility  The OpenAPI specification of all endpoints in Version 0.9.0 is intended to be backwards compatible with version 1.0.0 once released, so that there is little risk that clients working with this spec will break after the release of 1.0.0. Additional endpoints (such as retrieving mempool contents) are planned to be added.  ## Rosetta  The Data API and Construction API is inspired from [Rosetta API](https://www.rosetta-api.org/) most notably:   * Use of a JSON-Based RPC protocol on top of HTTP Post requests   * Use of Operations, Amounts, and Identifiers as universal language to   express asset movement for reading and writing  There are a few notable exceptions to note:   * Fetching of ledger data is through a Transaction stream rather than a   Block stream   * Use of `EntityIdentifier` rather than `AccountIdentifier`   * Use of `OperationGroup` rather than `related_operations` to express related   operations   * Construction endpoints perform coin selection on behalf of the caller.   This has the unfortunate effect of not being able to support high frequency   transactions from a single account. This will be addressed in future updates.   * Construction endpoints are online rather than offline as required by Rosetta  Future versions of the api will aim towards a fully-compliant Rosetta API.  ## Client Reference Implementation  > IMPORTANT > > The Network Gateway service is subject to substantial change before official release in v1.  We are currently working on a client reference implementation to the Core API, which we are happy to share with you for reference, as a demonstration of how to interpret responses from the Core API:  * [Latest - more functionality, no guarantees of correctness](https://github.com/radixdlt/radixdlt-network-gateway/) * [Stable - old code, ingesting balance and transfer data, manually tested against stokenet](https://github.com/radixdlt/radixdlt-network-gateway/tree/v0.1_BalanceSubstatesAndHistory)  As a starter, check out the folder `./src/DataAggregator/LedgerExtension` for understanding how to parse the contents of the transaction stream.  ## Client Code Generation  We have found success with generating clients against the [api.yaml specification](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/api.yaml) in the core folder. See https://openapi-generator.tech/ for more details.  The OpenAPI generator only supports openapi version 3.0.0 at present, but you can change 3.1.0 to 3.0.0 in the first line of the spec without affecting generation.  # Data API Flow  Integrators can make use of the Data API to synchronize a full or partial view of the ledger, transaction by transaction.  ![Data API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/documentation/data_sequence_flow.png)  # Construction API Flow  Integrators can make use of the Construction API to construct and submit transactions to the network.  ![Construction API Flow](https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/documentation/construction_sequence_flow.png)  Unlike the Rosetta Construction API [specification](https://www.rosetta-api.org/docs/construction_api_introduction.html), this Construction API selects UTXOs on behalf of the caller. This has the unfortunate side effect of not being able to support high frequency transactions from a single account due to UTXO conflicts. This will be addressed in a future release.
 *
 * The version of the OpenAPI document: 0.9.0
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
 * ConstructionDeriveRequestMetadata
 */
@JsonPropertyOrder({
  ConstructionDeriveRequestMetadata.JSON_PROPERTY_TYPE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-12-07T21:52:45.961651-06:00[America/Chicago]")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataAccount.class, name = "Account"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataAccount.class, name = "ConstructionDeriveRequestMetadataAccount"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataExitingUnstakes.class, name = "ConstructionDeriveRequestMetadataExitingUnstakes"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataPreparedStakes.class, name = "ConstructionDeriveRequestMetadataPreparedStakes"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataPreparedUnstakes.class, name = "ConstructionDeriveRequestMetadataPreparedUnstakes"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataToken.class, name = "ConstructionDeriveRequestMetadataToken"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataValidator.class, name = "ConstructionDeriveRequestMetadataValidator"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataValidatorSystem.class, name = "ConstructionDeriveRequestMetadataValidatorSystem"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataExitingUnstakes.class, name = "ExitingUnstakes"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataPreparedStakes.class, name = "PreparedStakes"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataPreparedUnstakes.class, name = "PreparedUnstakes"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataToken.class, name = "Token"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataValidator.class, name = "Validator"),
  @JsonSubTypes.Type(value = ConstructionDeriveRequestMetadataValidatorSystem.class, name = "ValidatorSystem"),
})

public class ConstructionDeriveRequestMetadata {
  public static final String JSON_PROPERTY_TYPE = "type";
  private String type;


  public ConstructionDeriveRequestMetadata type(String type) {
    this.type = type;
    return this;
  }

   /**
   * Get type
   * @return type
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_TYPE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getType() {
    return type;
  }


  @JsonProperty(JSON_PROPERTY_TYPE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setType(String type) {
    this.type = type;
  }


  /**
   * Return true if this ConstructionDeriveRequestMetadata object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConstructionDeriveRequestMetadata constructionDeriveRequestMetadata = (ConstructionDeriveRequestMetadata) o;
    return Objects.equals(this.type, constructionDeriveRequestMetadata.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ConstructionDeriveRequestMetadata {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
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
  mappings.put("Account", ConstructionDeriveRequestMetadataAccount.class);
  mappings.put("ConstructionDeriveRequestMetadataAccount", ConstructionDeriveRequestMetadataAccount.class);
  mappings.put("ConstructionDeriveRequestMetadataExitingUnstakes", ConstructionDeriveRequestMetadataExitingUnstakes.class);
  mappings.put("ConstructionDeriveRequestMetadataPreparedStakes", ConstructionDeriveRequestMetadataPreparedStakes.class);
  mappings.put("ConstructionDeriveRequestMetadataPreparedUnstakes", ConstructionDeriveRequestMetadataPreparedUnstakes.class);
  mappings.put("ConstructionDeriveRequestMetadataToken", ConstructionDeriveRequestMetadataToken.class);
  mappings.put("ConstructionDeriveRequestMetadataValidator", ConstructionDeriveRequestMetadataValidator.class);
  mappings.put("ConstructionDeriveRequestMetadataValidatorSystem", ConstructionDeriveRequestMetadataValidatorSystem.class);
  mappings.put("ExitingUnstakes", ConstructionDeriveRequestMetadataExitingUnstakes.class);
  mappings.put("PreparedStakes", ConstructionDeriveRequestMetadataPreparedStakes.class);
  mappings.put("PreparedUnstakes", ConstructionDeriveRequestMetadataPreparedUnstakes.class);
  mappings.put("Token", ConstructionDeriveRequestMetadataToken.class);
  mappings.put("Validator", ConstructionDeriveRequestMetadataValidator.class);
  mappings.put("ValidatorSystem", ConstructionDeriveRequestMetadataValidatorSystem.class);
  mappings.put("ConstructionDeriveRequestMetadata", ConstructionDeriveRequestMetadata.class);
  JSON.registerDiscriminator(ConstructionDeriveRequestMetadata.class, "type", mappings);
}
}

