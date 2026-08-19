# SignPDF Play Console 출시 체크리스트

## 앱 기본 정보
- 패키지: `com.easternwood.signpdf`
- 현재 버전: `1.6.0`
- versionCode: `11`
- targetSdk: `36`
- 기본 언어: 한국어
- 지원 언어: 한국어, English
- 앱 유형: 앱
- 가격: 무료
- 카테고리: 도구

## UI/접근성 실기기 검수
- 첫 설치 후 한국어가 기본으로 표시되는지 확인
- 설정 → English 변경 시 홈/편집/설정/결제 문구가 영어로 전환되는지 확인
- English → 한국어 재전환 확인
- 런처 아이콘에서 `PDF` 세 글자가 원형/둥근 사각형 마스크에서도 모두 보이는지 확인
- Android 15/16에서 상태바·내비게이션바가 상단/하단 조작부를 가리지 않는지 확인
- 홈 히어로 카드의 PDF 열기/이미지 가져오기 터치 영역과 가독성 확인
- 최근 문서 3개 축약 표시 및 전체 보기/삭제 동작 확인
- 하단 홈/최근/도구/설정 이동 흐름 확인
- 작은 화면과 큰 글꼴 설정에서 주요 버튼/문구가 잘리지 않는지 확인
- PDF 편집 화면에서 문서 영역이 충분히 확보되는지 확인
- 색상/굵기/도구/Undo/Redo/저장/공유 버튼 터치 및 선택 상태 확인

## 기능 테스트
- 일반 PDF 열기 테스트
- 비밀번호 PDF 열기 → 암호 입력 → 정상 표시 테스트
- 비밀번호 PDF에 틀린 암호 입력 → 앱 종료/크래시 없이 재입력 가능 확인
- 비밀번호 PDF를 연 뒤 서명/필기 → 새 PDF 저장 → 다른 PDF 뷰어에서 확인
- 큰 파일 또는 증분 저장된 암호 PDF에서 암호 감지 확인
- JPG/PNG/BMP/WEBP → PDF 변환 테스트
- 다중 페이지 PDF 페이지 이동 테스트
- 손서명: 영역 지정 → 서명 작성 → 삽입 → 저장 테스트
- 펜/연필/형광펜/지우개 및 Undo/Redo 테스트
- 저장 후 PDF를 다른 뷰어에서 열어 결과 확인
- 저장 후 공유 테스트
- 큰 PDF에서 메모리/성능 확인
- 앱 종료 후 최근 문서 재열기 확인

## CI 자동 검증
`Build SignPDF` GitHub Actions에서 다음을 통과해야 합니다.
- `:app:testDebugUnitTest`
- Debug APK 빌드
- Release AAB 빌드
- `lintRelease`
- 업로드 키가 설정된 경우 AAB JAR 서명 검증

암호 PDF 감지 테스트에는 `/Encrypt` 항목이 파일 끝에서 2MB 이상 떨어진 경우와 버퍼 경계를 가로지르는 경우가 포함됩니다.

## 광고
개발/검증 중 기본값은 Google 공식 테스트 AdMob ID입니다.

Play 출시 전 GitHub Actions Secrets에 실제 값을 설정:
- `SIGNPDF_ADMOB_APP_ID`
- `SIGNPDF_ADMOB_BANNER_ID`

저장소에는 실제 운영 광고 ID나 키 파일을 직접 커밋하지 않습니다.

## SignPDF Pro
Play Console에서 1회성 인앱 상품 생성:
- Product ID: `signpdf_pro`
- 유형: 1회성 인앱 상품
- 핵심 혜택: 광고 제거

상품을 활성화하기 전에는 앱에서 Pro 상품 설정 대기 상태로 표시될 수 있습니다.

## Play 업로드 키 / AAB 서명
GitHub Actions Secrets에 다음 4개를 설정합니다.
- `SIGNPDF_KEYSTORE_BASE64`: 업로드 키 JKS 파일의 Base64 문자열
- `SIGNPDF_KEYSTORE_PASSWORD`: keystore 비밀번호
- `SIGNPDF_KEY_ALIAS`: key alias
- `SIGNPDF_KEY_PASSWORD`: key 비밀번호

4개 값이 모두 설정되면 Actions가 `SignPDF-v1.6.0-play-aab`를 생성하고 `jarsigner -verify`로 서명을 검증합니다.

서명 정보가 없으면 CI 검증용으로만 `SignPDF-v1.6.0-unsigned-aab`가 생성됩니다. 이 파일은 Play Console 업로드용으로 사용하지 않습니다.

## 개인정보처리방침
- `PRIVACY_POLICY_KO.md`를 공개 HTTPS 페이지로 게시
- Play Console 개인정보처리방침 URL 입력
- 앱 내 UMP 광고 개인정보 설정 동작 확인
- PDF 비밀번호가 기기 내 처리되고 저장/서버 전송되지 않는 설명과 실제 동작 일치 확인

## 데이터 보안
문서 내용은 SignPDF 자체 서버로 전송하지 않지만 Google Mobile Ads SDK와 Google Play Billing SDK가 포함됩니다.

따라서 Data Safety 작성 시 '앱 자체 서버에 문서를 업로드하지 않는다'와 '외부 SDK가 광고/구매 처리를 위해 일부 데이터를 처리할 수 있다'를 구분해서 작성합니다.

## 스토어 등록정보
- 앱 이름: `SignPDF - PDF 서명·필기`
- 짧은 설명/자세한 설명: `STORE_LISTING_KO.md` 사용
- 앱 아이콘 512×512: v1.6.0 블루 리뉴얼 디자인 사용
- 기능 그래픽 1024×500
- 휴대전화 스크린샷 최소 2장 이상
- 스크린샷에는 실제 개인정보가 포함된 문서를 사용하지 않기
- 한국어 스토어 등록정보를 기본으로 하고 필요 시 영어 등록정보 추가
- 암호 PDF 지원 문구는 실기기 최종 테스트 통과 후 등록정보에 반영

## 출시 순서
- GitHub Actions 전체 통과
- v1.6.0 실기기 UI/기능 테스트 통과
- 실제 AdMob ID 설정
- Pro 상품 생성 및 활성화
- 개인정보처리방침 공개 URL 준비
- Play 업로드 키 설정 후 `SignPDF-v1.6.0-play-aab` 생성
- 내부 테스트 → 비공개 테스트 → 프로덕션 순으로 진행

## 출시 전 금지/주의 문구
- '공인전자서명', '법적으로 인증된 서명' 등 인증 서비스로 오인할 표현 금지
- 현재 지원하지 않는 DOC/DOCX 변환 홍보 금지
- 현재 구현하지 않은 병합/텍스트 편집/검색 기능을 스토어 기능으로 홍보 금지
- 암호화/보안 기능을 실제보다 과장하지 않기
