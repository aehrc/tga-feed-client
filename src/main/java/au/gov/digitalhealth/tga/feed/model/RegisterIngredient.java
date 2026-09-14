package au.gov.digitalhealth.tga.feed.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class RegisterIngredient {

  @JsonProperty("Name")
  private String name;

  @JsonProperty("Strength")
  private String strength;

  @JsonProperty("FormulationType")
  private String formulationType;

  @JsonProperty("EquivDetail")
  private String equivDetail;

  /**
   * Whether this is an active ingredient rather than an excipient.
   *
   * <p>Deliberately an exact match, preserving the behaviour every existing authored product was
   * built with. Loosening it to ignore case would newly include ingredients previously treated as
   * inactive, changing the drafts this feeds — a content change that should be made knowingly
   * rather than as a side effect of consolidating this type.
   */
  @JsonIgnore
  public boolean isActive() {
    return "Active".equals(formulationType);
  }
}
