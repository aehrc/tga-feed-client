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
public class RegisterSearchResult {

  @JsonProperty("Results")
  private List<RegisterEntry> results;

  @JsonProperty("TotalRecords")
  private int totalRecords;

  @JsonProperty("RequestedPageStart")
  private int requestedPageStart;

  @JsonProperty("RequestedPageEnd")
  private int requestedPageEnd;
}
