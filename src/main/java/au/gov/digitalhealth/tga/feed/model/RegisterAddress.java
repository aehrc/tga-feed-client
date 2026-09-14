package au.gov.digitalhealth.tga.feed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Register wire type, shared by every service that reads the TGA ARTG feed.
 *
 * <p>Field names mirror the register's own JSON exactly. Unknown properties are ignored so a new
 * register field does not break existing readers.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterAddress {

  @JsonProperty("AddressLine1")
  private String addressLine1;

  @JsonProperty("AddressLine2")
  private String addressLine2;

  @JsonProperty("Suburb")
  private String suburb;

  @JsonProperty("State")
  private String state;

  @JsonProperty("Postcode")
  private String postcode;

  @JsonProperty("Country")
  private String country;
}
