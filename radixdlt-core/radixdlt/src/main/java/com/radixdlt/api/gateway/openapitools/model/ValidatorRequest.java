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
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;


/**
 * ValidatorRequest
 */
@JsonPropertyOrder({
  ValidatorRequest.JSON_PROPERTY_NETWORK,
  ValidatorRequest.JSON_PROPERTY_VALIDATOR_IDENTIFIER
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-12-02T13:18:58.008003-06:00[America/Chicago]")
public class ValidatorRequest {
  public static final String JSON_PROPERTY_NETWORK = "network";
  private String network;

  public static final String JSON_PROPERTY_VALIDATOR_IDENTIFIER = "validator_identifier";
  private ValidatorIdentifier validatorIdentifier;


  public ValidatorRequest network(String network) {
    this.network = network;
    return this;
  }

   /**
   * Get network
   * @return network
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_NETWORK)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getNetwork() {
    return network;
  }


  @JsonProperty(JSON_PROPERTY_NETWORK)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setNetwork(String network) {
    this.network = network;
  }


  public ValidatorRequest validatorIdentifier(ValidatorIdentifier validatorIdentifier) {
    this.validatorIdentifier = validatorIdentifier;
    return this;
  }

   /**
   * Get validatorIdentifier
   * @return validatorIdentifier
  **/
  @javax.annotation.Nonnull
  @ApiModelProperty(required = true, value = "")
  @JsonProperty(JSON_PROPERTY_VALIDATOR_IDENTIFIER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public ValidatorIdentifier getValidatorIdentifier() {
    return validatorIdentifier;
  }


  @JsonProperty(JSON_PROPERTY_VALIDATOR_IDENTIFIER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setValidatorIdentifier(ValidatorIdentifier validatorIdentifier) {
    this.validatorIdentifier = validatorIdentifier;
  }


  /**
   * Return true if this ValidatorRequest object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ValidatorRequest validatorRequest = (ValidatorRequest) o;
    return Objects.equals(this.network, validatorRequest.network) &&
        Objects.equals(this.validatorIdentifier, validatorRequest.validatorIdentifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(network, validatorIdentifier);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ValidatorRequest {\n");
    sb.append("    network: ").append(toIndentedString(network)).append("\n");
    sb.append("    validatorIdentifier: ").append(toIndentedString(validatorIdentifier)).append("\n");
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

}
