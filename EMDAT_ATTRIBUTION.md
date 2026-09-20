# EM-DAT Historical Dataset — Attribution and Integrity Note

> Factual record of what this repository contains about the EM-DAT historical
> dataset. This is **not legal advice**. Before redistributing the data (or
> publishing analysis derived from it), consult EM-DAT/CRED's current terms and
> attribution requirements — no licence text was found in this repository (see
> "Licensing limitation" below).

## Source (verbatim from the export itself)

* Maintainer: **EM-DAT, CRED / UCLouvain, Brussels, Belgium**
* Glossary / public-table documentation: <https://doc.emdat.be/docs/data-structure-and-content/emdat-public-table/>
* Dataset version: **2026-09-11**
* Export file creation: **Fri, 18 Sep 2026 15:01:31 UTC**
* Table type: **public_emdat_custom_request**
* Sheets in the export: `EM-DAT Data`, `EM-DAT Info`

The attribution above is read from the export's own `EM-DAT Info` sheet at
prepare time — it is never hardcoded in the app.

## Raw source vs derived assets

* **Raw source (NOT shipped in the APK, NOT tracked):**
  `public_emdat_custom_request_2026-09-18_8d824e09-042e-4b45-9d7c-26f6439cf38a.xlsx`
  (238,686 bytes, repo root). Keep it out of version control and out of
  `app/src/main/assets`.
* **Derived assets (transformations, shipped as app assets):**
  * `app/src/main/assets/emdat/emdat_india_historical.csv` — 740 data rows
    (741 lines with header), 30 columns, original values preserved.
  * `app/src/main/assets/emdat/emdat_dataset_info.json` — the attribution
    block (source, glossary, version, file creation, table type, record
    counts) copied verbatim from the export.
* **Transformation:** `tools/emdat_prepare.py` (stdlib-only, idempotent:
  output depends only on the input file). It keeps the 30 columns the app
  consumes and drops the rest of the 47-column export (aid contributions,
  appeals, CPI, river basin, …) rather than shipping unused data. Records
  without an EM-DAT identifier are dropped at prepare time because they can
  neither be deduplicated nor attributed.

## Verified contents (with sources)

| Fact | Value | Verified by |
| ---- | ----- | ----------- |
| India records accepted, none rejected | 740 / 0 | `EmdatImportTest` (bundled-asset import test) + CSV line count (741 = header + 740) |
| Records with their own source coordinates (mappable) | 94 | `EmdatImportTest` ("only the 94 records … are ever mappable") |
| Context-only records (no coordinates, never mapped) | 646 | 740 − 94, asserted in the same test |
| Countries | India only | `EmdatImportTest` (distinct countries == ["India"]) |
| Year coverage | 1900–2026 | `EmdatImportTest` (min/max `startYear`) |
| Pilot-area records (Idukki / Kerala matches) | 3 / 49 | `EmdatImportTest` ("the pilot area records are the three known EM-DAT entries") |

Do not quote different counts: these are the only numbers supported by the
repository contents and its tests.

## Historical-only rules (enforced in code and tests)

* The panel header reads **HISTORICAL DATA — NOT LIVE HAZARDS**; every record
  carries its source, version, spatial precision, and the mandatory disclaimer.
* The historical map layer is **off by default** (`isHistoricalLayerOn = false`);
  only the 94 records with EM-DAT's own coordinates can ever be drawn.
* Context-only records keep the source's location text exactly and are never
  pinned to placeholder coordinates; missing impact figures render
  **"Not available"**, never 0.
* Historical records **never feed** current risk verdicts, danger-zone
  decisions, official red-zone claims, population demand, shelter capacity, or
  routing: there are zero historical references in `data/risk`, and the
  capacity engine maps a HISTORICAL classification to ESTIMATED, never
  MEASURED.

## Licensing limitation

No licence, permission, or redistribution terms were found in the export's
info sheet, the prepare script, or anywhere else in this repository — only
the source name, glossary link, version, and creation date above. In
particular, **no unrestricted-redistribution right is claimed here**.
Anyone reusing the raw XLSX or the derived CSV/JSON outside this app must
check EM-DAT/CRED's current terms first and preserve the in-app credit
(source • version • glossary) wherever the data is shown.
