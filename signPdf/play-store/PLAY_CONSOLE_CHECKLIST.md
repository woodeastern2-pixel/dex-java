# SignPDF Play Console 출시 체크리스트

## 앱 기본 정보
- 패키지: `com.easternwood.signpdf`
- 현재 버전: `1.8.4`
- versionCode: `23`
- targetSdk: `36`
- 기본 언어: 한국어
- 지원 언어: 한국어, English
- 앱 유형: 앱
- 가격: 무료 + 1회성 Pro 인앱 구매
- 카테고리: 도구

## UI/접근성 실기기 검수
- 첫 설치 후 한국어가 기본으로 표시되는지 확인
- 최초 실행 시 기본 PDF 저장 폴더 지정 안내 및 설정에서 변경 가능 안내 확인
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

## 핵심 기능 테스트
- 일반 PDF 열기 테스트
- 비밀번호 PDF 열기 → 암호 입력 → 정상 표시 테스트
- 비밀번호 PDF에 틀린 암호 입력 → 앱 종료/크래시 없이 재입력 가능 확인
- 비밀번호 PDF를 연 뒤 서명/필기 → 새 PDF 저장 → 다른 PDF 뷰어에서 확인
- JPG/PNG/BMP/WEBP → PDF 변환 테스트
- 손서명: 영역 지정 → 서명 작성 → 삽입 → 저장 테스트
- 펜/연필/형광펜/지우개 및 Undo/Redo 테스트
- 저장 전 파일명 변경 테스트
- 설정에서 지정한 기본 폴더에 저장되는지 확인
- 저장 후 공유 테스트
- 앱 종료 후 최근 문서 재열기 확인

## PDF 병합 / Pro 도구 테스트
- PDF 병합 → Android 기본 파일 선택기에서 PDF만 표시되는지 확인
- 여러 PDF 다중 선택 → 확인 → SignPDF 순서 지정 화면 진입 확인
- 첫 페이지 썸네일, 파일명, 페이지 수 미리보기 확인
- 위/아래 순서 이동 및 파일 제거 확인
- 최종 `병합하시겠습니까?` 확인 후 병합 성공 확인
- 병합 후 파일명 변경 및 기본 저장 위치 저장 확인
- 페이지 회전/삭제/순서 변경/추출/분할 확인
- 고급 이미지→PDF의 다중 이미지, 순서, 용지 크기, 여백, 품질 설정 확인
- 저장 서명을 다른 문서에서 재사용 확인

## 무료 사용 횟수
- 무료 사용자는 성공한 결과물 생성/저장 작업만 1회 차감되는지 확인
- 단순 PDF 열기 및 암호 PDF 열기에는 차감되지 않는지 확인
- 실패한 작업은 차감되지 않는지 확인
- 10회 소진 시 Pro 안내 및 작업 제한 확인
- 월간 주기 초기화 로직 확인
- Pro 활성화 시 제한 없이 동작하는지 확인

## Google Play Billing / SignPDF Pro
Play Console에서 1회성 인앱 상품 생성 및 활성화:
- Product ID: `signpdf_pro`
- 유형: 1회성 비소비성 인앱 상품
- 혜택: 작업 무제한, 광고 제거, 서명 저장, PDF 병합·페이지 관리·고급 PDF 도구

비공개 테스트에서 반드시 확인:
- Play에서 설치한 Release 빌드에서 `Pro 구매` 버튼에 실제 가격 표시
- 버튼 터치 시 Google Play 결제 시트 표시
- 라이선스 테스터 `항상 승인` 테스트 결제로 구매 성공
- PURCHASED 상태에서만 Pro 활성화
- 구매 완료 직후 광고 제거 및 Pro 도구 활성화
- 앱 재실행 후 구매 자동 복원
- `구매 복원` 동작 확인
- 라이선스 테스터 구매가 3분 후 자동 환불되지 않는지 확인하여 acknowledge 성공 검증
- `항상 거부` 및 사용자 취소 시 Pro가 열리지 않는지 확인
- PENDING 결제에서는 Pro를 열지 않고 완료 후 자동 활성화되는지 확인

## 광고 / UMP
개발·Debug 빌드는 Google 공식 테스트 AdMob ID를 사용합니다.

Play용 Release 전에 GitHub Actions Secrets에 실제 값을 설정:
- `SIGNPDF_ADMOB_APP_ID`
- `SIGNPDF_ADMOB_BANNER_ID`

v1.8.4부터 실제 AdMob ID가 없으면 CI 산출물을 `NOT-FOR-PLAY`로 표시하며 Play-ready AAB를 생성하지 않습니다.

비공개 테스트에서 확인:
- 무료 사용자에게 배너 광고가 표시되는지 확인
- Pro 구매 직후 배너가 제거되는지 확인
- 앱 재실행 후 Pro 사용자에게 광고가 다시 나타나지 않는지 확인
- 광고 개인정보 설정이 필요한 지역에서는 UMP Privacy Options Form이 열리는지 확인
- 개인정보 설정이 필요하지 않거나 아직 상태 확인 중이면 버튼 터치에 안내가 표시되는지 확인
- 실제 AdMob 광고 단위로 테스트할 때 테스트 기기를 사용하고 임의로 실제 광고를 클릭하지 않기

## CI 자동 검증
`Build SignPDF` GitHub Actions에서 다음을 통과해야 합니다.
- `:app:testDebugUnitTest`
- Debug APK 빌드
- Release AAB 빌드
- `lintRelease`
- 업로드 키가 설정된 경우 AAB JAR 서명 검증
- 실제 AdMob ID + 업로드 키가 모두 설정된 경우에만 `SignPDF-v1.8.4-play-aab` 생성

## Play 업로드 키 / AAB 서명
GitHub Actions Secrets에 다음 4개를 설정합니다.
- `SIGNPDF_KEYSTORE_BASE64`: 업로드 키 JKS 파일의 Base64 문자열
- `SIGNPDF_KEYSTORE_PASSWORD`: keystore 비밀번호
- `SIGNPDF_KEY_ALIAS`: key alias
- `SIGNPDF_KEY_PASSWORD`: key 비밀번호

4개 값과 실제 AdMob ID가 모두 설정된 Play-ready AAB만 비공개 테스트에 업로드합니다.

## 개인정보처리방침
- `PRIVACY_POLICY_KO.md`를 공개 HTTPS 페이지로 게시
- Play Console 개인정보처리방침 URL 입력
- 앱 내 UMP 광고 개인정보 설정 동작 확인
- 문서 내용 및 PDF 비밀번호가 기기 내 처리되고 서버로 올라가지 않는 설명과 실제 동작 일치 확인

## 데이터 보안
문서 내용은 SignPDF 서버로 전송하지 않지만 Google Mobile Ads SDK와 Google Play Billing SDK가 포함됩니다.

따라서 Data Safety 작성 시 '문서를 서버에 업로드하지 않는다'와 '외부 SDK가 광고/구매 처리를 위해 일부 데이터를 처리할 수 있다'를 구분해서 작성합니다.

## 스토어 등록정보
- 앱 이름: `SignPDF - PDF 서명·필기`
- 짧은 설명/자세한 설명: `STORE_LISTING_KO.md` 사용
- 앱 아이콘 512×512 준비
- 기능 그래픽 1024×500 준비
- 휴대전화 스크린샷 4장 이상 준비 권장
- 스크린샷에는 실제 개인정보가 포함된 문서를 사용하지 않기
- 한국어 스토어 등록정보를 기본으로 하고 필요 시 영어 등록정보 추가

## 비공개 테스트 업로드 전 최종 순서
1. 최신 CI 전체 통과
2. 실제 AdMob 앱/배너 ID 생성 및 Actions Secrets 설정
3. Play Console `signpdf_pro` 상품 생성·활성화 및 가격 설정
4. 개인정보처리방침 공개 HTTPS URL 준비
5. Play 업로드 키 Actions Secrets 설정
6. `SignPDF-v1.8.4-play-aab` 생성 및 서명 검증
7. Play Console 비공개 테스트에 AAB 업로드
8. 라이선스 테스터 계정으로 Pro 구매/복원/거부/PENDING 테스트
9. 무료 광고 노출 및 Pro 광고 제거 실기기 확인
10. 테스터 참여 링크 배포

## 출시 전 금지/주의 문구
- '공인전자서명', '법적으로 인증된 서명' 등 인증 서비스로 오인할 표현 금지
- 현재 지원하지 않는 DOC/DOCX 변환 홍보 금지
- 문서 암호화/보안 기능을 실제보다 과장하지 않기
