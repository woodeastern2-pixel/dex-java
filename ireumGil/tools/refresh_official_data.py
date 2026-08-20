#!/usr/bin/env python3
"""Refresh IreumBom's offline naming data from public source pages.

The generated files are app assets, so the Android app remains useful offline.
Run from the repository root:

    python3 ireumGil/tools/refresh_official_data.py

Sources:
* Supreme Court e-Family name statistics API (current cumulative Top 10)
* Supreme Court e-Family personal-name Hanja search (allowed Hanja)
* Historical registered-name syllables (2008-2019 public statistics mirror)
"""

from __future__ import annotations

import csv
import io
import json
import re
import time
import urllib.parse
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
HANJA_OUTPUT = ASSETS / "hanja" / "official_person_name_hanja_full.csv"
RANK_OUTPUT = ASSETS / "names" / "popular_names_current.csv"

EFAMILY_ROOT = "https://efamily.scourt.go.kr"
HANJA_URL = f"{EFAMILY_ROOT}/webhanja/whjsearch"
RANK_URL = f"{EFAMILY_ROOT}/ds/report/query.do"
HISTORICAL_URLS = (
    "https://raw.githubusercontent.com/randkid/name/master/m.csv",
    "https://raw.githubusercontent.com/randkid/name/master/f.csv",
)
NOW_KST = datetime.now(ZoneInfo("Asia/Seoul"))
SOURCE_VERSION = NOW_KST.strftime("%Y-%m-%d")
UNIHAN_URL = "https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip"

COMMON_SURNAME_READINGS = set(
    "김 이 박 최 정 강 조 윤 장 임 한 오 서 신 권 황 안 송 전 홍 유 고 문 양 손 배 조 백 허 남 심 노 하 곽 성 차 주 우 구 민 진 지 엄 채 원 천 방 공 현 함 변 염 여 추 도 소 석 선 설 마 길 연 위 표 명 기 반 왕 금 옥 육 인 맹 제 모 탁 국 어 은 편 용 예 경 봉 사 부 가 복 태 목 형 피 두 감 음 빈 동 온 호 범 좌 팽 승 간 상 시 갈 단 순"
    .split()
)


def request_bytes(url: str, data: bytes | None = None, attempts: int = 4) -> bytes:
    headers = {
        "User-Agent": "IreumBomDataRefresh/1.0 (+offline Android dataset)",
        "Accept": "application/json,text/plain,*/*",
    }
    if data is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    error: Exception | None = None
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(
                urllib.request.Request(url, data=data, headers=headers), timeout=45
            ) as response:
                return response.read()
        except Exception as exc:  # network refresh tool: retry transient failures
            error = exc
            time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"Could not download {url}: {error}")


def load_registered_name_readings() -> list[str]:
    readings = set(COMMON_SURNAME_READINGS)
    for url in HISTORICAL_URLS:
        content = request_bytes(url).decode("utf-8-sig", errors="replace")
        for row in csv.DictReader(io.StringIO(content)):
            name = (row.get("name") or "").strip()
            try:
                count = int(row.get("weight") or 0)
            except ValueError:
                continue
            if count < 3 or not re.fullmatch(r"[가-힣]{1,5}", name):
                continue
            readings.update(name)
    return sorted(readings)


def clean_meaning(reading: str, raw: str) -> str:
    value = (raw or "").strip()
    value = re.sub(rf"^{re.escape(reading)}\s*:\s*", "", value)
    value = re.sub(r"\s+", " ", value)
    return value or f"{reading} 음의 인명용 한자"


def load_unicode_strokes() -> dict[str, int]:
    archive = zipfile.ZipFile(io.BytesIO(request_bytes(UNIHAN_URL)))
    strokes: dict[str, int] = {}
    with archive.open("Unihan_IRGSources.txt") as source:
        for raw in io.TextIOWrapper(source, encoding="utf-8"):
            if "\tkTotalStrokes\t" not in raw:
                continue
            code, _, values = raw.strip().split("\t", 2)
            try:
                strokes[chr(int(code[2:], 16))] = int(values.split()[0])
            except (ValueError, IndexError):
                continue
    return strokes


def reading_element(reading: str) -> str:
    if not reading or not ("가" <= reading[0] <= "힣"):
        return ""
    initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    initial = initials[(ord(reading[0]) - 0xAC00) // 588]
    if initial in "ㄱㄲㅋ": return "목"
    if initial in "ㄴㄷㄸㄹㅌ": return "화"
    if initial in "ㅇㅎ": return "토"
    if initial in "ㅅㅆㅈㅉㅊ": return "금"
    return "수"


def fetch_hanja_for_reading(reading: str) -> list[dict[str, str]]:
    query = urllib.parse.urlencode(
        {
            "mode": "listUnicodeByKsnd",
            "ksnd": f"{ord(reading):x}",
            "ext": "0",
            "pgmode": "1",
            "pgno": "1",
            "pgsize": "10000",
        }
    )
    payload = json.loads(request_bytes(f"{HANJA_URL}?{query}").decode("utf-8"))
    rows = []
    for item in payload.get("resultlist", []):
        if int(item.get("isin") or 0) != 1:
            continue
        code = str(item.get("cd") or "").strip()
        try:
            character = chr(int(code, 16))
        except (TypeError, ValueError, OverflowError):
            continue
        rows.append(
            {
                "character": character,
                "koreanReading": reading,
                "meaning": clean_meaning(reading, str(item.get("in") or item.get("dic") or "")),
            }
        )
    return rows


def refresh_hanja() -> int:
    readings = load_registered_name_readings()
    unicode_strokes = load_unicode_strokes()
    collected: dict[tuple[str, str], dict[str, str]] = {}
    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = {executor.submit(fetch_hanja_for_reading, r): r for r in readings}
        for index, future in enumerate(as_completed(futures), 1):
            reading = futures[future]
            try:
                rows = future.result()
            except Exception as exc:
                print(f"warning: skipped {reading}: {exc}")
                continue
            for row in rows:
                collected[(row["character"], row["koreanReading"])] = row
            if index % 100 == 0:
                print(f"hanja readings: {index}/{len(readings)}")

    HANJA_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "id", "character", "koreanReading", "meaning", "strokeCount", "radical",
        "fiveElement", "allowedForName", "isAdditionalNameHanja",
        "isBasicEducationHanja", "isVariant", "variantOf", "isCommonSurname",
        "genderPreference", "source", "sourceVersion", "sourceNote",
    ]
    rows = sorted(collected.values(), key=lambda r: (r["koreanReading"], r["character"]))
    with HANJA_OUTPUT.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for index, row in enumerate(rows, 1):
            writer.writerow(
                {
                    "id": 100000 + index,
                    **row,
                    "strokeCount": unicode_strokes.get(row["character"], ""),
                    "fiveElement": reading_element(row["koreanReading"]),
                    "allowedForName": "true",
                    # Surname status is curated separately because a surname reading does
                    # not make every Hanja with the same sound a valid family-name Hanja.
                    "isCommonSurname": "false",
                    "genderPreference": "NEUTRAL",
                    "source": "Supreme Court e-Family personal-name Hanja search | Unicode Unihan",
                    "sourceVersion": SOURCE_VERSION,
                    "sourceNote": "인명용 여부·뜻·독음은 법원 조회, 획수는 Unicode Unihan, 오행은 한글 초성 발음오행 분류",
                }
            )
    return len(rows)


def rank_query(gender_code: str) -> bytes:
    current_month = NOW_KST.strftime("%Y%m")
    params = {
        "@MultiCandType": {"value": ["YM"], "type": "STRING", "defaultValue": ""},
        "@MultiCandStDt": {"value": ["200801"], "type": "STRING", "defaultValue": ""},
        "@MultiCandEdDt": {"value": [current_month], "type": "STRING", "defaultValue": ""},
        "@SidoCd": {
            "value": ["11", "26", "27", "28", "29", "30", "31", "36", "41", "43", "42", "44", "45", "46", "47", "48", "50", "22", "21", "23", "24", "25"],
            "type": "STRING", "defaultValue": "[All]", "whereClause": "C.SIDO_CD",
        },
        "@CggCd": {"value": ["_EMPTY_VALUE_"], "type": "STRING", "defaultValue": "[All]", "whereClause": "D.CGG_CD"},
        "@UmdCd": {"value": ["_EMPTY_VALUE_"], "type": "STRING", "defaultValue": "[All]", "whereClause": "E.UMD_CD"},
        "@GenderCd": {"value": [gender_code], "type": "STRING", "defaultValue": "[All]", "whereClause": "F.GENDER_CD"},
    }
    return urllib.parse.urlencode(
        {
            "pid": "1811", "uid": "99999", "dsid": "1261", "dstype": "DS",
            "sqlid": "1811-1", "params": json.dumps(params, ensure_ascii=False),
        }
    ).encode("utf-8")


def refresh_rankings() -> int:
    RANK_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for gender, code in (("M", "1"), ("F", "2")):
        payload = json.loads(request_bytes(RANK_URL, rank_query(code)).decode("utf-8"))
        for item in payload.get("data", [])[:10]:
            rows.append(
                {
                    "gender": gender,
                    "rank": item["순위"],
                    "name": item["이름"],
                    "count": item["건수"],
                    "periodStart": "2008-01",
                    "periodEnd": NOW_KST.strftime("%Y-%m"),
                    "updatedAt": SOURCE_VERSION,
                    "source": "Supreme Court e-Family name statistics",
                }
            )
    with RANK_OUTPUT.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0].keys(), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


if __name__ == "__main__":
    hanja_count = refresh_hanja()
    rank_count = refresh_rankings()
    print(f"wrote {hanja_count} Hanja rows to {HANJA_OUTPUT}")
    print(f"wrote {rank_count} ranking rows to {RANK_OUTPUT}")
