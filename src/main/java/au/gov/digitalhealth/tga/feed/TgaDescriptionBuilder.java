package au.gov.digitalhealth.tga.feed;

import lombok.Builder;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Builds the HTML table used as a register-derived ticket's description.
 *
 * <p>Ported from Sergio's {@code HtmlDescription} — including row order and the exact anchor
 * attributes — so descriptions composed here stay byte-identical to the ones the feed processor
 * wrote before composition moved to this service. Parity matters: the description is overwritten on
 * every update, so any markup drift would rewrite every ticket on the first run after cutover.
 */
@Builder
public class TgaDescriptionBuilder {

  private final String summaryUrl;
  private final String consumerInformationUrl;
  private final String productInformationUrl;
  private final String entryType;

  public String createTable() {
    Document document = new Document("html");
    Element tableWrap = document.appendElement("div");
    Element table = tableWrap.appendElement("table");
    Element tbody = table.appendElement("tbody");

    appendLinkRow(tbody, "ARTG Summary URL", summaryUrl, true);
    appendEntryTypeRow(tbody);
    appendLinkRow(tbody, "Consumer Information URL", consumerInformationUrl, false);
    appendLinkRow(tbody, "Product Information Url", productInformationUrl, false);
    return document.toString();
  }

  /**
   * @param always when false the row is omitted for a null/blank URL, matching the original's
   *     behaviour of always emitting the summary row but skipping empty document links
   */
  private void appendLinkRow(Element tbody, String heading, String url, boolean always) {
    if (!always && (url == null || url.isEmpty())) {
      return;
    }
    Element row = tbody.appendElement("tr");
    row.appendElement("th").text(heading);
    Element cell = row.appendElement("td").addClass("confluenceTd");
    cell.appendElement("a")
        .attr("href", url == null ? "" : url)
        .attr("class", "external-link")
        .attr("target", "_blank")
        .attr("rel", "nofollow noopener")
        .text(url == null ? "" : url);
  }

  private void appendEntryTypeRow(Element tbody) {
    Element row = tbody.appendElement("tr");
    row.appendElement("th").text("TGA entry type");
    row.appendElement("td").text(entryType == null ? "" : entryType);
  }
}
