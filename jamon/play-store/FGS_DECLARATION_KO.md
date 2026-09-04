# 잠온 포그라운드 서비스 선언 자료

## 선언할 서비스 유형

`mediaPlayback`

## Play Console 설명문

잠온은 사용자가 수면 루틴을 시작했을 때 선택한 환경음을 화면이 꺼지거나 다른 앱을 사용하는 동안에도 중단 없이 재생하기 위해 미디어 재생 포그라운드 서비스를 사용합니다. 서비스는 사용자의 명시적인 루틴 시작 동작으로만 시작되며, 실행 중에는 재생 상태와 정지 동작을 제공하는 지속 알림을 표시합니다. 사용자가 알림에서 정지를 누르거나 루틴 시간이 끝나면 오디오와 서비스를 종료합니다. 광고·마케팅·데이터 동기화에는 이 서비스를 사용하지 않습니다.

## English declaration

Jamon uses a media-playback foreground service only after the user explicitly starts a sleep routine. It keeps the selected ambient audio playing while the screen is off or another app is in use. While active, a persistent notification shows playback status and a stop action. The audio and service stop when the user taps Stop in the notification or when the routine timer ends. The service is not used for advertising, marketing, or data synchronisation.

## 제출 영상 촬영 순서

Galaxy S26 Ultra의 실제 출시 후보 빌드로 한 번에 이어서 촬영합니다.

1. 홈 화면에서 루틴 시간과 사운드가 보이게 합니다.
2. `오늘의 잠 루틴 시작`을 눌러 실제 사운드가 재생되는 것을 보여줍니다.
3. Android 홈으로 이동해도 사운드가 계속 들리는 것을 보여줍니다.
4. 화면을 껐다가 다시 켜도 재생이 이어지는 것을 보여줍니다.
5. 알림 패널을 열어 잠온의 지속 알림과 정지 동작을 보여줍니다.
6. 알림의 정지를 눌러 사운드와 알림이 함께 끝나는 것을 보여줍니다.

영상에는 기기 전체 화면, 사용자 동작, 앱 이름, 지속 알림이 명확히 보여야 합니다. 기존 `1000143579.mp4`는 앱 화면과 루틴 설정은 확인되지만 홈 이동·화면 꺼짐·지속 알림·알림 정지 장면이 없어 포그라운드 서비스 신고 영상으로는 사용하지 않습니다.

## 코드 대조 기준

- 권한: `android.permission.FOREGROUND_SERVICE`
- 권한: `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- 서비스 유형: `android:foregroundServiceType="mediaPlayback"`
- 시작 조건: 사용자가 수면 루틴을 시작한 경우
- 종료 조건: 타이머 만료 또는 사용자의 정지 동작
- 알림 용도: 재생 상태 표시와 정지 제어

