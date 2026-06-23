# dex-java

## 이름길 공식 한자 데이터 구조

이 프로젝트의 `ireumGil` 앱은 인명용 한자 데이터를 하드코딩 샘플이 아니라,
`app/src/main/assets/hanja/` 경로의 공식 출처 기반 파일을 로드하도록 구성되어 있습니다.

지원 파일:

- `official_person_name_hanja.csv`
- `official_person_name_hanja.json`
- `official_allowed_variants.csv`
- `official_basic_education_hanja.csv`

필드 스키마:

- `id`
- `character`
- `koreanReading`
- `meaning`
- `strokeCount`
- `radical`
- `fiveElement`
- `allowedForName`
- `isAdditionalNameHanja`
- `isBasicEducationHanja`
- `isVariant`
- `variantOf`
- `isCommonSurname`
- `genderPreference`
- `source`
- `sourceVersion`
- `sourceNote`

주의:

- 현재 저장소에 포함된 자산 파일은 파이프라인 검증용 예시 데이터입니다.
- 전체 공식 인명용 한자셋(약 9,000+)을 사용하려면 아래 업데이트 절차로 파일을 교체하세요.
- 오행/성별 추천 등 성명학 보완값은 공식 원자료와 분리해 해석해야 합니다.

## 공식 인명용 한자 데이터 업데이트 방법

1. 대한민국 법원 전자가족관계등록시스템에서 최신 인명용한자표(PDF)를 내려받습니다.
2. 법제처 국가법령정보센터에서 「가족관계의 등록 등에 관한 규칙」 제37조 및 별표를 확인합니다.
	- 인명용추가한자표
	- 인명용한자허용자체표
3. PDF/표 원문을 앱 런타임에서 직접 파싱하지 말고, 사전 변환으로 CSV 또는 JSON으로 정리합니다.
	- 권장 워크플로: `official PDF -> 추출/정제 CSV/JSON -> 앱 assets 반영`
4. 변환 결과 파일을 `ireumGil/app/src/main/assets/hanja/`에 배치합니다.
5. 데이터셋 버전을 올립니다.
	- `com.ireumgil.data.HanjaAssetImporter#DATASET_VERSION`
6. 아래 명령으로 앱을 다시 빌드합니다.
	- `./gradlew assembleDebug`

## 데이터 출처 라벨링 규칙

- `source`, `sourceVersion`, `sourceNote`는 반드시 유지합니다.
- 공식 출처가 다른 데이터는 행 단위로 명시합니다.
  - 대법원 인명용 한자표
  - 국가법령정보센터 별표
  - 교육부 한문교육용 기초한자
- 누락된 공식 값(뜻/획수/부수/오행)은 비워 둘 수 있습니다. 임의 추정값을 공식값처럼 채우지 않습니다.