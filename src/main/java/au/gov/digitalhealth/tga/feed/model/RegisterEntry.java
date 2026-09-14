package au.gov.digitalhealth.tga.feed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * A single ARTG register entry, as returned by the TGA.
 *
 * <p>The union of what every consuming service needs, so no service silently drops a field another
 * one records. That matters because the entry is snapshotted onto tickets and compared later: a
 * field dropped by one writer reads as a content change to the next reader.
 *
 * <p>{@code ProductCategory} is carried as the register's own string rather than a typed enum —
 * mapping it to a service's internal model is that service's concern, not the register's.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterEntry {

  @JsonProperty("LicenceId")
  private String licenceId;

  @JsonProperty("Name")
  private String name;

  @JsonProperty("EntryType")
  private String entryType;

  @JsonProperty("Status")
  private String status;

  @JsonProperty("StartDate")
  private String startDate;

  @JsonProperty("BlackTriangleSchemeFlag")
  private String blackTriangleSchemeFlag;

  @JsonProperty("ProductCategory")
  private String productCategory;

  @JsonProperty("ApprovalArea")
  private String approvalArea;

  @JsonProperty("LicenceClass")
  private String licenceClass;

  @JsonProperty("Conditions")
  private List<String> conditions;

  @JsonProperty("Products")
  private List<RegisterProduct> products;

  @JsonProperty("ConsumerInformation")
  private RegisterDocument consumerInformation;

  @JsonProperty("ProductInformation")
  private RegisterDocument productInformation;

  @JsonProperty("Sponsor")
  private RegisterSponsor sponsor;

  @JsonProperty("Manufacturers")
  private List<RegisterManufacturer> manufacturers;

  @JsonProperty("DeviceProductNames")
  private List<String> deviceProductNames;

  @JsonProperty("AnnualChargeExemptWaverFlag")
  private String annualChargeExemptWaverFlag;

  @JsonProperty("AnnualChargeExemptWaverDate")
  private String annualChargeExemptWaverDate;

  @JsonProperty("NilTurnoverFlag")
  private String nilTurnoverFlag;

  @JsonProperty("NilTurnoverDate")
  private String nilTurnoverDate;
}
