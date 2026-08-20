#!/usr/bin/env python3
"""Refresh Korean surname candidates from the 2015 census surname table.

The canonical table is KOSIS DT_1IN15SB. KOSIS currently places the table
behind its interactive SSO viewer, so this tool reads a UTF-8 HTML mirror of
the published census rows and keeps the KOSIS table id in every output row.
"""

from __future__ import annotations

import csv
import html
import re
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "hanja" / "official_common_surnames.csv"
MIRROR_URL = "https://www.rootsinfo.co.kr/info/roots/table_sung15.php"
KOSIS_TABLE_ID = "DT_1IN15SB"
SOURCE = "통계청 2015 인구주택총조사 성씨·본관별 인구"
SOURCE_VERSION = "2015"

ROW_PATTERN = re.compile(
    r'<tr><td class="num">(?P<rank>\d+)</td>'
    r'<td class="sung">(?P<label>[^<]+)</td>'
    r'<td class="total">(?P<population>[\d,]+)</td></tr>'
)
LABEL_PATTERN = re.compile(r"^(?P<reading>[^()]+)\((?P<character>[^()]+)\)$")


def is_hanja_text(value: str) -> bool:
    if not value:
        return False
    return all(
        "CJK" in unicodedata_name(character)
        or 0x3400 <= ord(character) <= 0x4DBF
        or 0x4E00 <= ord(character) <= 0x9FFF
        or 0x20000 <= ord(character) <= 0x323AF
        for character in value
    )


def unicodedata_name(character: str) -> str:
    import unicodedata

    return unicodedata.name(character, "")


def fetch_rows() -> list[dict[str, str]]:
    request = urllib.request.Request(
        MIRROR_URL,
        headers={"User-Agent": "IreumOn surname data refresh/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        page = response.read().decode("utf-8")

    rows: list[dict[str, str]] = []
    skipped: list[str] = []
    for match in ROW_PATTERN.finditer(page):
        label = html.unescape(match.group("label")).strip()
        parsed = LABEL_PATTERN.match(label)
        if not parsed:
            skipped.append(label)
            continue

        reading = parsed.group("reading").strip()
        character = parsed.group("character").strip()
        if not is_hanja_text(character):
            # The census export contains one unresolved placeholder, 諸*.
            skipped.append(label)
            continue

        population = match.group("population").replace(",", "")
        rows.append(
            {
                "character": character,
                "koreanReading": reading,
                "population": population,
            }
        )

    if len(rows) < 500:
        raise RuntimeError(f"Expected at least 500 usable census surnames, got {len(rows)}")
    if len({(row['character'], row['koreanReading']) for row in rows}) != len(rows):
        raise RuntimeError("Duplicate surname rows found in census source")

    jeong = {row["character"] for row in rows if row["koreanReading"] == "정"}
    expected_jeong = {"鄭", "丁", "程", "政", "桯", "定", "正", "情"}
    if not expected_jeong.issubset(jeong):
        raise RuntimeError(f"Incomplete 정 surname set: {sorted(jeong)}")

    if skipped != ["제갈(諸*)"]:
        raise RuntimeError(f"Unexpected unimportable census labels: {skipped}")
    return rows


def write_rows(rows: list[dict[str, str]]) -> None:
    fieldnames = [
        "id",
        "character",
        "koreanReading",
        "meaning",
        "allowedForName",
        "isCommonSurname",
        "genderPreference",
        "source",
        "sourceVersion",
        "sourceNote",
    ]
    with OUTPUT.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        for offset, row in enumerate(rows, start=1):
            writer.writerow(
                {
                    "id": 200000 + offset,
                    "character": row["character"],
                    "koreanReading": row["koreanReading"],
                    "meaning": "",
                    "allowedForName": "",
                    "isCommonSurname": "true",
                    "genderPreference": "NEUTRAL",
                    "source": SOURCE,
                    "sourceVersion": SOURCE_VERSION,
                    "sourceNote": f"전국 {int(row['population']):,}명 · KOSIS {KOSIS_TABLE_ID}",
                }
            )


def main() -> None:
    rows = fetch_rows()
    write_rows(rows)
    print(f"wrote {len(rows)} surname rows to {OUTPUT}")


if __name__ == "__main__":
    main()
