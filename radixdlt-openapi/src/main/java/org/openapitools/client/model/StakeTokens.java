/*
 * Wallet/Explorer Api
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 2.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package org.openapitools.client.model;

import java.util.Objects;
import java.util.Arrays;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import org.openapitools.client.model.Action;
import org.openapitools.client.model.StakeTokens;
import org.openapitools.client.model.StakeTokensAllOf;
import org.openapitools.client.model.TokenTransfer;

/**
 * StakeTokens
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-10-25T08:11:19.875906-07:00[America/Los_Angeles]")
public class StakeTokens extends Action {
  public static final String SERIALIZED_NAME_FROM = "from";
  @SerializedName(SERIALIZED_NAME_FROM)
  private String from;

  public static final String SERIALIZED_NAME_VALIDATOR = "validator";
  @SerializedName(SERIALIZED_NAME_VALIDATOR)
  private String validator;

  public static final String SERIALIZED_NAME_AMOUNT = "amount";
  @SerializedName(SERIALIZED_NAME_AMOUNT)
  private String amount;

  public StakeTokens() {
    this.type = this.getClass().getSimpleName();
  }

  public StakeTokens from(String from) {
    
    this.from = from;
    return this;
  }

   /**
   * Get from
   * @return from
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getFrom() {
    return from;
  }


  public void setFrom(String from) {
    this.from = from;
  }


  public StakeTokens validator(String validator) {
    
    this.validator = validator;
    return this;
  }

   /**
   * Get validator
   * @return validator
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getValidator() {
    return validator;
  }


  public void setValidator(String validator) {
    this.validator = validator;
  }


  public StakeTokens amount(String amount) {
    
    this.amount = amount;
    return this;
  }

   /**
   * Get amount
   * @return amount
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getAmount() {
    return amount;
  }


  public void setAmount(String amount) {
    this.amount = amount;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StakeTokens stakeTokens = (StakeTokens) o;
    return Objects.equals(this.from, stakeTokens.from) &&
        Objects.equals(this.validator, stakeTokens.validator) &&
        Objects.equals(this.amount, stakeTokens.amount) &&
        super.equals(o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, validator, amount, super.hashCode());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class StakeTokens {\n");
    sb.append("    ").append(toIndentedString(super.toString())).append("\n");
    sb.append("    from: ").append(toIndentedString(from)).append("\n");
    sb.append("    validator: ").append(toIndentedString(validator)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
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

