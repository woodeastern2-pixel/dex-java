# SignPDF Play Console 출시 체크리스트

## 앱 기본 정보
- 패키지: `com.easternwood.signpdf`
- 현재 버전: `1.3.0`
- versionCode: `4`
- targetSdk: `36`
- 기본 언어: 한국어
- 앱 유형: 앱
- 가격: 무료
- 카테고리: 도구

## 테스트/출시 전 필수
- 실제 Android 기기에서 PDF 열기 테스트
- JPG/PNG/BMP/WEBP → PDF 변환 테스트
- 다중 페이지 PDF 페이지 이동 테스트
- 손서명: 영역 지정 → 서명 작성 → 삽입 → 저장 테스트
- 펜/연필/형광펜/지우개 및 Undo/Redo 테스트
- 저장 후 PDF를 다른 뷰어에서 열어 결과 확인
- 저장 후 공유 테스트
- 큰 PDF에서 메모리/성능 확인

## 광고
개발 중 기본값은 Google 공식 테스트 AdMob ID입니다.

Play 출시 전에 실제 값으로 교체:
- `ADMOB_APP_ID`
- `ADMOB_BANNER_ID`

권장: Gradle property 또는 CI secret에서 주입하고 저장소에 실제 운영 비밀값을 직접 커밋하지 않습니다.

## SignPDF Pro
Play Console에서 1회성 인앱 상품 생성:
- Product ID: `signpdf_pro`
- 유형: 1회성 인앱 상품
- 핵심 혜택: 광고 제거

상품을 활성화하기 전에는 앱에서 'Pro 상품 설정 대기 중'으로 표시될 수 있습니다.

## 개인정보처리방침
- `PRIVACY_POLICY_KO.md` 초안을 공개 HTTPS 페이지로 게시
- Play Console 개인정보처리방침 URL 입력
- 앱 내 UMP 광고 개인정보 설정 동작 확인

## 데이터 보안
문서 내용은 SignPDF 자체 서버로 전송하지 않지만 Google Mobile Ads SDK와 Google Play Billing SDK가 포함됩니다.

따라서 Data Safety 작성 시 '앱 자체 서버에 문서를 업로드하지 않는다'와 '외부 SDK가 광고/구매 처리를 위해 일부 데이터를 처리할 수 있다'를 구분해서 작성합니다.

## 스토어 등록정보
- 앱 이름: `SignPDF - PDF 서명·필기`
- 짧은 설명/자세한 설명: `STORE_LISTING_KO.md` 사용
- 앱 아이콘 512×512
- 기능 그래픽 1024×500
- 휴대전화 스크린샷 최소 2장 이상
- 스크린샷에는 실제 개인정보가 포함된 문서를 사용하지 않기

## 출시 빌드
- GitHub Actions Release AAB 생성 확인
- Play 업로드 키로 AAB 서명
- Play App Signing 사용
- 내부 테스트 → 비공개 테스트 → 프로덕션 순으로 진행

## 출시 전 금지/주의 문구
- '공인전자서명', '법적으로 인증된 서명' 등 인증 서비스로 오인할 표현 금지
- 현재 지원하지 않는 DOC/DOCX 변환 홍보 금지
- 암호화/보안 기능을 실제보다 과장하지 않기
