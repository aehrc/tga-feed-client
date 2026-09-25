# tga-feed-client

Client and shared domain rules for the TGA ARTG register feed.

Two services read the Australian Therapeutic Goods Administration's ARTG register:
the feed processor that scans it nightly, and the submission gateway that looks up
individual registrations. They each had their own client, their own model of an
entry, and their own reading of the register's quirks — and those drifted. One
applied a retry policy and the other applied none; one reconciled the register's
habit of answering a licence-ID query with unrelated entries and the other left
that to its callers.

This library is the one place that knows how to read the register.

## What it provides

| | |
|---|---|
| `TgaFeedClient` | Reactive transport: paging, retries, licence-ID reconciliation |
| `TgaRegisterClient` | Blocking, cached lookup by ARTG ID, with one failure type |
| `RegisterEntry` and `model/*` | The register's wire shape, as the union of what consumers need |
| `RegisterSnapshot` | The snapshot recorded on a ticket, for detecting change |
| `RegisterSnapshotNormaliser` | Strips the register's meaningless variation before comparison |
| `TgaRegisterRules`, `TgaScheduleRules` | Facts about entries: types, schedules, summary URLs |
| `TgaDescriptionBuilder` | The register description table |

## Use

```xml
<dependency>
  <groupId>au.gov.digitalhealth</groupId>
  <artifactId>tga-feed-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

In a Spring Boot application the clients are supplied by auto-configuration. Two
properties are required and have no default, because a library must not choose an
application's environment:

```properties
tga.feed.base-url=https://data.tga.gov.au
tga.feed.search-uri=/ARTGSearch/ARTGWebService.svc/json/ARTGValueSearch/
```

Everything else — the register's query parameter names, timeouts, buffer size and
retry policy — defaults to values known to work and can be overridden under the
same `tga.feed.` prefix.

Outside Spring Boot, construct `TgaFeedClient` or `TgaRegisterClient` directly
with a `TgaFeedProperties`.

## Why it is versioned separately

It tracks an external system's contract, on that system's schedule. Hosting it
inside an application's build would tie register-integration releases to an
application that may not have changed, and force consumers to take a whole
application version bump to pick up a register fix. This is the same reasoning
that keeps the Snowstorm client separate.

## Two things worth knowing before changing it

**`RegisterSnapshot`'s serialised shape is a cross-service contract.** A snapshot
written by whichever service creates a ticket is read back and compared on every
later run. Renaming a field there makes every affected ticket read as changed
forever, not once.

**The normalisation rules are load-bearing.** The register does not return stable
JSON for unchanged data — it revises `EffectiveDate` on identical content, returns
collections in unstable order, and emits blank filler that comes and goes. A
regression in `RegisterSnapshotNormaliser` means every entry looks changed on
every run.
