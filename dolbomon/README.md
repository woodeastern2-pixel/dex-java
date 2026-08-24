# 돌봄온 (DolbomOn)

요양원·요양병원·돌봄 현장에서 간병인/요양보호사가 어르신의 하루를 빠르게 기록하고 보호자에게 공유하기 위한 Android 앱입니다.

## 현재 버전
- 앱 이름: 돌봄온
- 패키지: `com.easternwood.dolbomon`
- 버전: `0.1.0` (versionCode 1)
- Android: minSdk 24 / targetSdk 36
- 자체 서버 및 회원가입 없음
- 주요 데이터는 기기 내부 암호화 저장
- 한국어 / English / 中文 / Tiếng Việt
- 수익화: 무료 + AdMob 배너 광고

## 핵심 기능
- 어르신 및 여러 보호자 등록
- 식사, 수분, 컨디션, 기분, 수면, 배변, 활동, 특이사항을 버튼으로 기록
- 체크 결과를 자연스러운 보호자 전달 문장으로 자동 생성
- 필요물품 수량·긴급도 기록 및 문장 자동 반영
- 사진 촬영/첨부 및 앱 전용 저장공간 보관
- 음성 입력과 자주 쓰는 문구
- 문자, 카카오톡, 이메일 및 Android 공유로 전달
- 보호자 다중 선택 후 순차 공유
- 날짜별 기록, 사진 앨범, 최근 7일 돌봄앨범 공유
- PIN/생체인증/자동 잠금, 화면 캡처 및 최근 앱 화면 보호
- 암호화 백업/복원, 전체 데이터 삭제
- 앱 내 개인정보처리방침

## 소스 보관 형식
현재 연결된 GitHub 쓰기 환경이 UTF-8 텍스트 파일 쓰기만 안정적으로 지원하므로 전체 Android 소스는 `source.tar.gz.b64`에 압축·Base64 형태로 보관합니다. CI에서 자동으로 풀어 정상 Gradle 프로젝트로 빌드합니다.

로컬 복원:
```bash
mkdir dolbomon-src
base64 -d source.tar.gz.b64 | tar -xz -C dolbomon-src
cd dolbomon-src
gradle :app:assembleDebug
```

확정된 밝은 하늘색 돌봄온 아이콘 원본도 압축된 프로젝트 내부 `app/icon-source.b64`에 포함되어 있으며, 빌드 시 실제 런처 PNG로 생성됩니다.
