/// Demo Mode 서비스
/// 전체 플랫폼 기능을 3분 안에 자동으로 시연

import 'package:ai_voc_assistant/domain/entities/voc_entity.dart';
import 'package:uuid/uuid.dart';

/// 데모 단계
enum DemoPhase {
  idle,          // 준비
  generating,    // 샘플 데이터 생성
  analyzing,     // AI 분석
  workflow,      // 워크플로우 실행
  notifications, // Teams/Slack 알림
  dashboard,     // 대시보드 업데이트
  completed,     // 완료
}

/// 데모 진행 상태
class DemoStatus {
  final DemoPhase phase;
  final int progressPercent; // 0-100
  final String message;
  final List<String> logs; // 시연 로그
  final Duration elapsed;
  final Duration? totalDuration;

  DemoStatus({
    required this.phase,
    required this.progressPercent,
    required this.message,
    required this.logs,
    required this.elapsed,
    this.totalDuration,
  });

  double get percentComplete {
    if (totalDuration == null) return 0;
    return elapsed.inMilliseconds / totalDuration!.inMilliseconds;
  }
}

/// 데모 실행 콜백
typedef DemoProgressCallback = void Function(DemoStatus status);

/// Demo Mode 서비스
abstract class DemoModeService {
  /// 데모 시작
  Future<void> startDemo(DemoProgressCallback onProgress);

  /// 데모 중지
  Future<void> stopDemo();

  /// 진행 상태 조회
  DemoStatus? getCurrentStatus();

  /// 데모 중인지 확인
  bool isRunning();
}

/// 기본 Demo Mode 구현
class DefaultDemoModeService implements DemoModeService {
  DemoStatus? _currentStatus;
  bool _isRunning = false;
  final Duration _demoDuration = const Duration(minutes: 3);

  @override
  Future<void> startDemo(DemoProgressCallback onProgress) async {
    if (_isRunning) return;
    _isRunning = true;

    final startTime = DateTime.now();
    final logs = <String>[];

    // Phase 1: 샘플 데이터 생성
    await _runPhase(
      phase: DemoPhase.generating,
      duration: const Duration(seconds: 20),
      startTime: startTime,
      logs: logs,
      onProgress: onProgress,
      task: () async {
        logs.add('📝 샘플 VOC 생성 중...');
        logs.add('✓ 10개 VOC 생성 완료');
        logs.add('✓ 음성 VOC 1건 추가');
        logs.add('✓ 이미지 VOC 2건 추가');
        logs.add('✓ 문서 VOC 1건 추가');
      },
    );

    // Phase 2: AI 분석
    await _runPhase(
      phase: DemoPhase.analyzing,
      duration: const Duration(seconds: 30),
      startTime: startTime,
      logs: logs,
      onProgress: onProgress,
      task: () async {
        logs.add('🤖 AI 분석 진행 중...');
        logs.add('✓ VOC-001: 긴급도 HIGH');
        logs.add('✓ VOC-002: 담당자 추천 - 김팀장');
        logs.add('✓ VOC-003: 카테고리 분류 - 장애');
        logs.add('✓ 중복 VOC 2건 검출');
        logs.add('✓ 답변 초안 생성 완료');
      },
    );

    // Phase 3: 워크플로우 실행
    await _runPhase(
      phase: DemoPhase.workflow,
      duration: const Duration(seconds: 30),
      startTime: startTime,
      logs: logs,
      onProgress: onProgress,
      task: () async {
        logs.add('⚙️ 워크플로우 자동 실행...');
        logs.add('✓ [1/12] VOC 수신');
        logs.add('✓ [2/12] 업무 관련성 판정');
        logs.add('✓ [3/12] 카테고리 분류');
        logs.add('✓ [4/12] 긴급도 예측');
        logs.add('✓ [5/12] 중복 검사');
        logs.add('✓ [6/12] 유사 VOC 검색');
        logs.add('✓ [7/12] 담당부서 추천');
        logs.add('✓ [8/12] 담당자 추천');
        logs.add('✓ [9/12] 답변 초안 생성');
        logs.add('✓ [10/12] JIRA 생성 추천');
        logs.add('✓ [11/12] Teams 알림 발송');
        logs.add('✓ [12/12] 승인 대기');
      },
    );

    // Phase 4: 알림 발송
    await _runPhase(
      phase: DemoPhase.notifications,
      duration: const Duration(seconds: 20),
      startTime: startTime,
      logs: logs,
      onProgress: onProgress,
      task: () async {
        logs.add('📬 외부 시스템 연동...');
        logs.add('✓ Teams 채널로 긴급 알림 발송');
        logs.add('✓ Slack #support 채널에 공유');
        logs.add('✓ JIRA 이슈 생성: SUPP-2024-001');
        logs.add('✓ Confluence FAQ 문서 작성');
      },
    );

    // Phase 5: 대시보드 업데이트
    await _runPhase(
      phase: DemoPhase.dashboard,
      duration: const Duration(seconds: 20),
      startTime: startTime,
      logs: logs,
      onProgress: onProgress,
      task: () async {
        logs.add('📊 대시보드 업데이트...');
        logs.add('✓ 전체 VOC: 1,234건 (+14)');
        logs.add('✓ 오픈: 45건 (-2)');
        logs.add('✓ 진행중: 28건 (+4)');
        logs.add('✓ 해결: 1,161건 (+12)');
        logs.add('✓ AI 활용률: 98.5%');
        logs.add('✓ 평균 처리시간: 4.2시간');
        logs.add('✓ ROI: \$125,000/월 절감');
      },
    );

    // Phase 6: 완료
    _currentStatus = DemoStatus(
      phase: DemoPhase.completed,
      progressPercent: 100,
      message: '✨ 데모 완료! 모든 기능이 정상 작동합니다.',
      logs: logs,
      elapsed: DateTime.now().difference(startTime),
      totalDuration: _demoDuration,
    );

    onProgress(_currentStatus!);
    _isRunning = false;
  }

  Future<void> _runPhase({
    required DemoPhase phase,
    required Duration duration,
    required DateTime startTime,
    required List<String> logs,
    required DemoProgressCallback onProgress,
    required Future<void> Function() task,
  }) async {
    final phaseStart = DateTime.now();

    while (DateTime.now().difference(phaseStart) < duration) {
      _currentStatus = DemoStatus(
        phase: phase,
        progressPercent:
            ((DateTime.now().difference(startTime).inMilliseconds /
                        _demoDuration.inMilliseconds) *
                    100)
                .toInt()
                .clamp(0, 99),
        message: _getPhaseMessage(phase),
        logs: List.from(logs),
        elapsed: DateTime.now().difference(startTime),
        totalDuration: _demoDuration,
      );

      onProgress(_currentStatus!);
      await Future.delayed(const Duration(milliseconds: 500));
    }

    // 단계별 작업 수행
    await task();
  }

  String _getPhaseMessage(DemoPhase phase) {
    switch (phase) {
      case DemoPhase.generating:
        return '📝 샘플 데이터 생성 중...';
      case DemoPhase.analyzing:
        return '🤖 AI 분석 진행 중...';
      case DemoPhase.workflow:
        return '⚙️ 워크플로우 자동 실행...';
      case DemoPhase.notifications:
        return '📬 외부 시스템 연동...';
      case DemoPhase.dashboard:
        return '📊 대시보드 업데이트...';
      case DemoPhase.completed:
        return '✨ 데모 완료!';
      default:
        return '준비 중...';
    }
  }

  @override
  Future<void> stopDemo() async {
    _isRunning = false;
  }

  @override
  DemoStatus? getCurrentStatus() {
    return _currentStatus;
  }

  @override
  bool isRunning() {
    return _isRunning;
  }
}
