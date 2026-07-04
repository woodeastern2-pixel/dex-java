# Sync Operations Guide

## 1) 필수 설정
- 모든 앱에서 동일한 `VOC 동기화 Bearer 토큰`을 설정합니다.
- 송신 앱은 `VOC 동기화 수신 URL 목록`에 대상 앱 주소를 등록합니다.
- 단건 동기화는 `/webhook/voc`, 전체 동기화는 `/webhook/sync/full`로 전송됩니다.

## 2) 수신기 상시 실행
수신 앱에서 아래 환경변수를 먼저 지정합니다.

```bash
export VOC_SYNC_HOST=0.0.0.0
export VOC_SYNC_PORT=8788
export VOC_SYNC_BEARER_TOKEN='운영토큰'
```

자동 시작 등록:

```bash
cd /workspaces/dex-java/ai_voc_assistant
./scripts/install_voc_sync_receiver_autostart.sh /경로/voc_assistant.db
```

상태 확인:

```bash
./scripts/status_voc_sync_receiver.sh
curl -sS http://127.0.0.1:8788/health
```

## 3) 운영 점검 시나리오
앱 화면에서 점검할 항목:
- 단건 VOC 등록 후 타 앱 동기화 반영
- 전체 VOC/매뉴얼 공유 전송 후 타 앱 반영
- 네트워크 장애 후 `실패 동기화 재시도` 버튼으로 재전송

## 4) CLI E2E 점검
로컬 수신기 API를 빠르게 점검할 때 사용합니다.

```bash
cd /workspaces/dex-java/ai_voc_assistant
chmod +x scripts/e2e_sync_check.sh
VOC_SYNC_BEARER_TOKEN='운영토큰' ./scripts/e2e_sync_check.sh /tmp/ai_voc_sync_e2e.db
```

검증 항목:
- 토큰 설정 시 무인증 401
- 인증 요청 200
- `voc.created`, `sync.full` 수신 성공
- health `voc_count >= 1`

## 5) 장애 대응
- 재시도 대기 건수는 설정 화면의 `실패 동기화 재시도 (N)`에서 확인합니다.
- 앱 재시작 후에도 대기 큐는 설정 저장소에 유지됩니다.
- 토큰 변경 시 송신/수신 앱 모두 같은 값으로 즉시 동기화해야 합니다.
