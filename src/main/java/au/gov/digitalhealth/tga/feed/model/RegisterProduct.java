package au.gov.digitalhealth.tga.feed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Register wire type, shared by every service that reads the TGA ARTG feed.
 *
 * <p>Field names mirror the register's own JSON exactly. Unknown properties are ignored so a new
 * register field does not break existing readers.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterProduct {

  @JsonProperty("Name")
  private String name;

  @JsonProperty("Type")
  private String type;

  @JsonProperty("EffectiveDate")
  private String effectiveDate;

  @JsonProperty("Ingredients")
  private List<RegisterIngredient> ingredients;

  @JsonProperty("Components")
  private List<RegisterComponent> components;

  @JsonProperty("Containers")
  private List<RegisterContainer> containers;

  @JsonProperty("Packs")
  private List<RegisterPack> packs;

  @JsonProperty("GMDNCode")
  private String gmdnCode;

  @JsonProperty("GMDNTerm")
  private String gmdnTerm;

  @JsonProperty("AdditionalInformation")
  private List<String> additionalInformation;

  @JsonProperty("SpecificIndications")
  private List<String> specificIndications;

  @JsonProperty("StandardIndications")
  private List<String> standardIndications;

  @JsonProperty("Warnings")
  private List<String> warnings;
}
