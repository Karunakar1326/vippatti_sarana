#!/usr/bin/env python3
"""EM-DAT XLSX -> prepared app assets.

WHY THIS EXISTS
---------------
EM-DAT's public export is an XLSX (OOXML) file. This app has no XLSX parser
dependency (Apache POI is not Android-friendly and would add megabytes), and the
raw export must NOT be shipped in the APK. So the export is normalised ONCE,
offline, into:

  app/src/main/assets/emdat/emdat_india_historical.csv
      trimmed, one row per EM-DAT record, ORIGINAL values preserved
  app/src/main/assets/emdat/emdat_dataset_info.json
      the attribution block (source, glossary, version, file creation, table
      type, record count) read VERBATIM from the export's "EM-DAT Info" sheet

The app then parses the CSV with its own tested parser, so every normalisation
decision (date validation, duplicate detection, null handling) lives in the
Kotlin code and its unit tests - not in this script.

Run:  python tools/emdat_prepare.py <export.xlsx> [output_dir]
It is idempotent: output depends only on the input file.
"""

import csv
import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"

# Only the fields the application actually consumes. Everything else in the
# 47-column export (aid contributions, appeals, CPI, river basin, ...) is
# deliberately dropped rather than shipped unused.
COLUMNS = [
    ("DisNo.", "disno"),
    ("Historic", "historic"),
    ("Disaster Group", "group"),
    ("Disaster Subgroup", "subgroup"),
    ("Disaster Type", "type"),
    ("Disaster Subtype", "subtype"),
    ("Event Name", "event_name"),
    ("ISO", "iso"),
    ("Country", "country"),
    ("Location", "location"),
    ("Admin Units", "admin_units"),
    ("Start Year", "start_year"),
    ("Start Month", "start_month"),
    ("Start Day", "start_day"),
    ("End Year", "end_year"),
    ("End Month", "end_month"),
    ("End Day", "end_day"),
    ("Total Deaths", "total_deaths"),
    ("No. Injured", "no_injured"),
    ("No. Affected", "no_affected"),
    ("No. Homeless", "no_homeless"),
    ("Total Affected", "total_affected"),
    ("Total Damage ('000 US$)", "damage_thousand_usd"),
    ("Total Damage, Adjusted ('000 US$)", "damage_adjusted_thousand_usd"),
    ("Magnitude", "magnitude"),
    ("Magnitude Scale", "magnitude_scale"),
    ("Latitude", "latitude"),
    ("Longitude", "longitude"),
    ("Entry Date", "entry_date"),
    ("Last Update", "last_update"),
]


def _column_index(cell_ref: str) -> int:
    letters = re.match(r"([A-Z]+)", cell_ref).group(1)
    n = 0
    for ch in letters:
        n = n * 26 + (ord(ch) - 64)
    return n - 1


def _read_sheet(archive: zipfile.ZipFile, path: str, shared: list) -> list:
    """Reads one worksheet into a list of {column_index: value} dicts."""
    rows = []
    for _event, element in ET.iterparse(archive.open(path), events=("end",)):
        if element.tag != NS + "row":
            continue
        cells = {}
        for cell in element:
            if cell.tag != NS + "c":
                continue
            kind = cell.get("t")
            index = _column_index(cell.get("r") or "A1")
            value = None
            for child in cell:
                if child.tag == NS + "v":
                    value = child.text
                elif child.tag == NS + "is":
                    value = "".join(t.text or "" for t in child.iter(NS + "t"))
            if value is None:
                continue
            if kind == "s":
                value = shared[int(value)]
            cells[index] = value
        rows.append(cells)
        element.clear()
    return rows


def main(argv: list) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    source = argv[1]
    out_dir = argv[2] if len(argv) > 2 else os.path.join(
        "app", "src", "main", "assets", "emdat"
    )

    archive = zipfile.ZipFile(source)
    shared_root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
    shared = [
        "".join(t.text or "" for t in item.iter(NS + "t"))
        for item in shared_root
    ]

    workbook = ET.fromstring(archive.read("xl/workbook.xml"))
    sheet_names = [
        sheet.get("name")
        for sheet in workbook.iter(NS + "sheet")
    ]

    data_rows = _read_sheet(archive, "xl/worksheets/sheet1.xml", shared)
    header = {index: data_rows[0].get(index) for index in range(len(data_rows[0]))}
    header_to_index = {name: index for index, name in header.items() if name}
    missing = [name for name, _ in COLUMNS if name not in header_to_index]
    if missing:
        raise SystemExit(f"export is missing expected columns: {missing}")

    os.makedirs(out_dir, exist_ok=True)
    csv_path = os.path.join(out_dir, "emdat_india_historical.csv")
    written = 0
    with open(csv_path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
        writer.writerow([slug for _, slug in COLUMNS])
        for row in data_rows[1:]:
            # A record with no EM-DAT identifier cannot be deduplicated or
            # attributed, so it is dropped here rather than shipped unusable.
            if not (row.get(header_to_index["DisNo."]) or "").strip():
                continue
            writer.writerow(
                [row.get(header_to_index[name], "") or "" for name, _ in COLUMNS]
            )
            written += 1

    # Attribution is read from the export itself - never hardcoded in the app.
    info_rows = _read_sheet(archive, "xl/worksheets/sheet2.xml", shared)
    info = {}
    for row in info_rows:
        cells = [row.get(i) for i in sorted(row)]
        cells = [c for c in cells if c is not None]
        if len(cells) >= 2:
            info[cells[0].strip().rstrip(":").strip()] = cells[1]
    info["# of records"] = info.get("# of records") or str(written)
    info["sheets"] = sheet_names
    info["prepared_record_count"] = str(written)
    info_path = os.path.join(out_dir, "emdat_dataset_info.json")
    with open(info_path, "w", encoding="utf-8") as handle:
        json.dump(info, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")

    print(f"records written: {written}")
    print(f"csv: {csv_path} ({os.path.getsize(csv_path)} bytes)")
    print(f"info: {info_path}")
    print(json.dumps(info, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
