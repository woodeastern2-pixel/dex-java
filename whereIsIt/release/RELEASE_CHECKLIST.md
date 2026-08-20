# 내 물건 어디있지? 1.2.0 출시 체크리스트

대상: `com.easternwood.whereisit`<br>
버전: `1.2.0 (versionCode 4)`

## 완료된 제품·기술 항목

- [x] 사진 없는 물건은 빈 이미지 없이 정보 중심 목록으로 표시
- [x] 사진 있는 물건은 사진 우선 카드로 표시
- [x] 입력 항목의 라벨과 안내 문구를 분리하여 큰 글자에서도 겹침 방지
- [x] 입력 화면 스크롤 및 하단 저장 버튼 여백 보정
- [x] 카메라 권한 요청 및 FileProvider 촬영 흐름의 예외 처리
- [x] 적응형 앱 아이콘 전경에 18dp 안전 여백 적용
- [x] Android 13 이상 테마 아이콘용 단색 아이콘 제공
- [x] 하단 적응형 배너 광고 및 콘텐츠/FAB 충돌 방지
- [x] 전면 광고를 자연스러운 화면 전환에만 제한
- [x] UMP 동의 상태가 광고 요청을 허용한 경우에만 광고 초기화
- [x] 개인정보 옵션 재진입 버튼 제공
- [x] 단위 테스트, APK/AAB 빌드 및 release lint를 수행하는 CI 구성
- [x] 실제 광고 ID·서명키 없는 상용 release 빌드 차단

## 광고 노출 정책

- 배너: 메인 화면 하단의 anchored adaptive banner 1개
- 전면: 세션 3분 이상 + 의미 있는 조회 4회 이상 + 직전 광고 후 20분 이상을 모두 만족할 때만 후보
- 전면 노출 지점: 물건 상세 화면에서 메인 화면으로 돌아온 직후
- 미노출 지점: 앱 시작, 물건 입력·수정 중, 저장 버튼 직후, 카메라·갤러리 전환 중

## 상용 출시 전에 계정 소유자가 입력할 값

GitHub Actions 저장소 비밀값에 아래 7개를 등록해야 합니다.

1. `WHEREISIT_ADMOB_APP_ID`
2. `WHEREISIT_ADMOB_BANNER_ID`
3. `WHEREISIT_ADMOB_INTERSTITIAL_ID`
4. `WHEREISIT_KEYSTORE_BASE64`
5. `WHEREISIT_KEYSTORE_PASSWORD`
6. `WHEREISIT_KEY_ALIAS`
7. `WHEREISIT_KEY_PASSWORD`

그 후 **Release WhereIsIt** 워크플로를 수동 실행합니다. 이 워크플로는 실제 광고 ID와 Play 업로드 서명을 검증한 뒤 production APK/AAB와 release lint 결과를 생성합니다.

## Play Console에서 완료할 항목

- [ ] AdMob에서 앱·배너·전면 광고 단위를 생성하고 3개 ID 확인
- [ ] AdMob 개인정보 및 메시지에서 필요한 지역의 동의 메시지 게시
- [ ] Play App Signing 등록 및 업로드 키 준비
- [ ] 개인정보처리방침 URL 등록
- [ ] 데이터 보안 설문 최종 검토·제출
- [ ] 광고 포함 여부를 `예`로 표시
- [ ] 콘텐츠 등급, 대상 연령, 앱 액세스, 스토어 등록정보 입력
- [ ] 내부 테스트 트랙에서 카메라·갤러리·백업·광고 동의·광고 닫기 실기기 점검
- [ ] Android vitals의 비정상 종료·ANR 확인 후 프로덕션 단계적 출시

## 산출물의 구분

- QA APK: 설치·기능 검증용이며 Google 공식 테스트 광고 ID 사용
- QA AAB: 번들 생성·Play 업로드 사전 검증용이며 Google 공식 테스트 광고 ID 사용
- Production APK/AAB: 위 비밀값이 모두 등록된 **Release WhereIsIt** 워크플로에서만 생성

테스트 광고 ID가 포함된 QA 산출물은 수익화용으로 게시하지 않습니다.
