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
public class RegisterContainer {

  @JsonProperty("Type")
  private String type;

  @JsonProperty("Closure")
  private String closure;

  @JsonProperty("Conditions")
  private List<String> conditions;

  @JsonProperty("LifeTime")
  private String lifeTime;

  @JsonProperty("Material")
  private String material;

  @JsonProperty("Temperature")
  private String temperature;
}
