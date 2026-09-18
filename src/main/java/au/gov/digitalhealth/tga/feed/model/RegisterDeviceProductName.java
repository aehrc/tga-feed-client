package au.gov.digitalhealth.tga.feed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Register wire type, shared by every service that reads the TGA ARTG feed.
 *
 * <p>Field names mirror the register's own JSON exactly. Unknown properties are ignored so a new
 * register field does not break existing readers.
 *
 * <p>Carried only by {@code Medical Device Included} entries, and empty on almost all of them — the
 * register returns {@code [{"Name": "..."}]} here, not a bare list of strings.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterDeviceProductName {

  @JsonProperty("Name")
  private String name;
}
