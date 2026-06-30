import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../../core/constants/app_constants.dart';
import '../../core/database/database_helper.dart';
import '../../data/services/connectors/default_connector_registry.dart';
import '../../data/services/excel_service.dart';
import '../../core/utils/vector_utils.dart';
import '../../domain/entities/response_entity.dart';
import '../../domain/entities/voc_entity.dart';
import '../../domain/repositories/voc_repository.dart';
import 'settings_viewmodel.dart';

class IntegrationViewModel extends ChangeNotifier {
  final VocRepository _vocRepository;
  final SettingsViewModel _settingsViewModel;
  final _uuid = const Uuid();

  final _excel = ExcelService();
  late final DefaultConnectorRegistry _connectors;

  bool _isLoading = false;
  String? _error;
  String? _success;
  List<String> _lastImportInvalidRows = [];

  IntegrationViewModel(this._vocRepository, this._settingsViewModel) {
    _connectors = DefaultConnectorRegistry(_settingsViewModel);
  }

  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get success => _success;
  List<String> get lastImportInvalidRows => _lastImportInvalidRows;

  void clearMessages() {
    _error = null;
    _success = null;
    notifyListeners();
  }

  Future<int> importVocFromExcel(String filePath) async {
    return importVocFromFile(filePath, duplicateStrategy: 'skip');
  }

  Future<int> importVocFromFile(
    String filePath, {
    String duplicateStrategy = 'skip',
  }) async {
    _start();
    _lastImportInvalidRows = [];
    try {
      final rows = await _excel.importVocRows(filePath);
      final existingVocs = await _vocRepository.getAllVocs();
      final existingMap = {
        for (final voc in existingVocs) _duplicateKey(voc): voc,
      };

      int imported = 0;
      int updated = 0;
      int skipped = 0;
      int invalid = 0;

      for (int index = 0; index < rows.length; index++) {
        final row = rows[index];
        final titleRaw =
            (row['VOC 제목'] ?? row['voc 제목'] ?? row['title'] ?? row['제목'] ?? '')
                .trim();
        final contentRaw =
            (row['VOC 내용'] ?? row['voc 내용'] ?? row['content'] ?? row['내용'] ?? '')
                .trim();
        final title = titleRaw.isEmpty ? '제목없음-${index + 2}' : titleRaw;
        final content = contentRaw.isEmpty ? '내용 없음' : contentRaw;
        final answers = _extractAnswers(row);

        final projectName = (row['프로젝트명'] ?? row['project'] ?? '').toString().trim();
        final businessType = (row['업무 구분'] ?? row['business_type'] ?? '').toString().trim();
        final projectCode =
            (row['프로젝트 코드'] ?? row['project_code'] ?? '').toString().trim();
        final vocNumber =
            (row['VOC 번호'] ?? row['voc_number'] ?? '').toString().trim();
        final project = _buildProjectDisplay(
          projectName: projectName,
          projectCode: projectCode,
          vocNumber: vocNumber,
        );

        final key = _duplicateKeyByText(title, content);
        final existing = existingMap[key];
        final shouldOverwrite = existing != null && duplicateStrategy == 'overwrite';
        final shouldSkip = existing != null && duplicateStrategy == 'skip';

        final now = DateTime.now();
        final voc = VocEntity(
          id: shouldOverwrite ? existing!.id : _uuid.v4(),
          title: title,
          content: content,
          category: (row['카테고리'] ?? row['category'] ?? '기능문의').trim(),
          tags: _optionalText(row['tags'] ?? row['태그']),
          customer: _requiredText(row['고객명'] ?? row['customer'], fallback: '미입력'),
          project: project,
          priority: _excel.normalizePriority(
            row['우선순위'] ?? row['priority'] ?? 'MEDIUM',
          ),
          status: _requiredText(row['status'], fallback: AppConstants.vocStatusOpen),
          urgency: null,
          businessType: businessType.isEmpty ? null : businessType,
          department: null,
          assignee: null,
          source: 'excel',
          sourceRef: vocNumber.isEmpty ? filePath : vocNumber,
          createdAt: existing?.createdAt ?? now,
          updatedAt: now,
        );

        if (shouldSkip) {
          skipped += 1;
          continue;
        }

        final savedVoc = shouldOverwrite
            ? await _vocRepository.updateVoc(voc.copyWith(updatedAt: now))
            : await _vocRepository.createVoc(voc);

        if (shouldOverwrite) {
          final db = await DatabaseHelper.instance.database;
          await db.delete(
            AppConstants.tableResponses,
            where: 'voc_id = ?',
            whereArgs: [savedVoc.id],
          );
        }

        for (final answer in answers) {
          final response = ResponseEntity(
            id: _uuid.v4(),
            vocId: savedVoc.id,
            content: answer,
            status: AppConstants.responseApproved,
            aiGenerated: false,
            adoptionCount: 1,
            usageCount: 1,
            lastUsedAt: now,
            approvedBy: 'Import',
            approvedAt: now,
            createdAt: now,
            updatedAt: now,
          );
          await _vocRepository.createResponse(response);
        }

        if (shouldOverwrite) {
          updated += 1;
        } else {
          imported += 1;
          existingMap[key] = savedVoc;
        }
      }

      _success =
          'VOC 가져오기 완료: 추가 $imported건, 갱신 $updated건, 건너뜀 $skipped건, 필수값 누락 $invalid건';
      return imported + updated;
    } catch (e) {
      _error = '엑셀 Import 실패: $e';
      return 0;
    } finally {
      _end();
    }
  }

  Future<String?> exportVocToExcel(String filePath) async {
    _start();
    try {
      final vocs = await _vocRepository.getAllVocs();
      final db = await DatabaseHelper.instance.database;
      final responses = await db.query(AppConstants.tableResponses);
      final out = await _excel.exportVocs(
        filePath: filePath,
        vocs: vocs,
        responses: responses,
      );
      _success = 'VOC/답변 Export 완료: $out';
      return out;
    } catch (e) {
      _error = 'VOC Export 실패: $e';
      return null;
    } finally {
      _end();
    }
  }

  Future<String?> exportVocTemplate(String filePath) async {
    _start();
    try {
      final out = await _excel.exportVocTemplate(filePath: filePath);
      _success = 'VOC 템플릿 다운로드 완료: $out';
      return out;
    } catch (e) {
      _error = 'VOC 템플릿 다운로드 실패: $e';
      return null;
    } finally {
      _end();
    }
  }

  Future<void> clearAllVocData() async {
    _start();
    try {
      final db = await DatabaseHelper.instance.database;

      // 업무 데이터 + AI 캐시 + 벡터 저장소를 함께 정리
      await db.delete('ai_chat_messages');
      await db.delete('ai_feedback');
      await db.delete(AppConstants.tableResponses);
      await db.delete(AppConstants.tableVocs);
      await db.delete(AppConstants.tableKnowledgeBase);
      await db.delete(AppConstants.tableJiraLinks);
      await db.delete(AppConstants.tableEmails);
      await db.delete(AppConstants.tableEmailAttachments);

      _success = 'VOC/Vector DB/AI 캐시를 모두 초기화했습니다.';
    } catch (e) {
      _error = 'VOC 초기화 실패: $e';
    } finally {
      _end();
    }
  }

  Future<int> rebuildVectorDb() async {
    _start();
    try {
      final vocs = await _vocRepository.getAllVocs();
      int updated = 0;
      for (final voc in vocs) {
        final next = voc.copyWith(
          embedding: VectorUtils.simpleTextEmbedding('${voc.title} ${voc.content}'),
          updatedAt: DateTime.now(),
        );
        await _vocRepository.updateVoc(next);
        updated += 1;
      }
      _success = 'Vector DB 재생성 완료: $updated건';
      return updated;
    } catch (e) {
      _error = 'Vector DB 재생성 실패: $e';
      return 0;
    } finally {
      _end();
    }
  }

  Future<void> clearAiCache() async {
    _start();
    try {
      final db = await DatabaseHelper.instance.database;
      await db.delete('ai_feedback');
      await db.delete('ai_chat_messages');
      _success = 'AI 캐시를 초기화했습니다.';
    } catch (e) {
      _error = 'AI 캐시 초기화 실패: $e';
    } finally {
      _end();
    }
  }

  String _duplicateKey(VocEntity voc) =>
      _duplicateKeyByText(voc.title, voc.content);

  String _duplicateKeyByText(String title, String content) =>
      '${title.trim().toLowerCase()}|${content.trim().toLowerCase()}';

  String? _optionalText(dynamic value) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? null : text;
  }

  String _requiredText(dynamic value, {required String fallback}) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? fallback : text;
  }

  String _buildProjectDisplay({
    required String projectName,
    required String projectCode,
    required String vocNumber,
  }) {
    final items = <String>[];
    if (projectName.trim().isNotEmpty) {
      items.add(projectName.trim());
    }
    if (projectCode.trim().isNotEmpty) {
      items.add(projectCode.trim().toUpperCase());
    }
    if (vocNumber.trim().isNotEmpty) {
      items.add(vocNumber.trim().toUpperCase());
    }
    if (items.isEmpty) return '미입력';
    return items.join(' | ');
  }

  List<String> _extractAnswers(Map<String, String> row) {
    final answers = <String>[];
    row.forEach((key, value) {
      final lower = key.trim().toLowerCase();
      final isAnswerColumn = lower == 'answer' ||
          lower == '답변' ||
          lower.startsWith('answer') ||
          lower.startsWith('답변');
      if (!isAnswerColumn) return;

      final raw = value.trim();
      if (raw.isEmpty) return;

      // 한 컬럼은 줄바꿈 포함 원문 그대로 1개의 답변으로 처리한다.
      answers.add(raw);
    });

    return answers;
  }

  Future<int> collectOutlookAndCreateVoc({int top = 20}) async {
    _start();
    try {
      final mails = await _connectors.outlookCollector.collectMails(top: top);

      final db = await DatabaseHelper.instance.database;
      int imported = 0;

      for (final m in mails) {
        final exists = await db.query(
          AppConstants.tableEmails,
          where: 'outlook_message_id = ?',
          whereArgs: [m.id],
          limit: 1,
        );
        if (exists.isNotEmpty) continue;

        final now = DateTime.now();
        final voc = VocEntity(
          id: _uuid.v4(),
          title: '[메일] ${m.subject}',
          content: m.bodyPreview,
          category: '운영문의',
          customer: m.sender.isEmpty ? '메일고객' : m.sender,
          project: '메일유입',
          priority: AppConstants.priorityMedium,
          status: AppConstants.vocStatusOpen,
          source: 'outlook',
          sourceRef: m.id,
          createdAt: now,
          updatedAt: now,
        );

        await _vocRepository.createVoc(voc);

        final emailId = _uuid.v4();
        await db.insert(AppConstants.tableEmails, {
          'id': emailId,
          'outlook_message_id': m.id,
          'sender': m.sender,
          'subject': m.subject,
          'body_preview': m.bodyPreview,
          'received_at': m.receivedAt?.toIso8601String(),
          'imported_voc_id': voc.id,
          'created_at': now.toIso8601String(),
        });

        for (final att in m.attachments) {
          final savedPath = await _connectors.outlookCollector.saveAttachment(att);
          if (savedPath == null) continue;
          await db.insert(AppConstants.tableEmailAttachments, {
            'id': _uuid.v4(),
            'email_id': emailId,
            'file_name': att.name,
            'file_path': savedPath,
            'content_type': att.contentType,
            'size': att.size,
            'created_at': now.toIso8601String(),
          });
        }

        imported += 1;
      }

      _success = 'Outlook 메일 기반 VOC $imported건 생성 완료';
      return imported;
    } catch (e) {
      _error = 'Outlook 연동 실패: $e';
      return 0;
    } finally {
      _end();
    }
  }

  Future<void> notifyUrgentVocToTeams(VocEntity voc) async {
    if (!_connectors.teamsNotifier.isConfigured) {
      _error = 'Teams Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _connectors.teamsNotifier.sendUrgentVoc(voc);
      _success = 'Teams 긴급 알림 전송 완료';
    } catch (e) {
      _error = 'Teams 알림 실패: $e';
    } finally {
      _end();
    }
  }

  Future<void> shareAiAnswerToTeams({
    required VocEntity voc,
    required String answer,
  }) async {
    if (!_connectors.teamsNotifier.isConfigured) {
      _error = 'Teams Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _connectors.teamsNotifier.shareAiAnswer(
        voc: voc,
        answer: answer,
      );
      _success = 'Teams AI 답변 공유 완료';
    } catch (e) {
      _error = 'Teams 공유 실패: $e';
    } finally {
      _end();
    }
  }

  Future<void> shareVocToSlack({
    required VocEntity voc,
  }) async {
    if (!_connectors.slackNotifier.isConfigured) {
      _error = 'Slack Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _connectors.slackNotifier.shareVoc(voc);
      _success = 'Slack VOC 공유 완료';
    } catch (e) {
      _error = 'Slack 공유 실패: $e';
    } finally {
      _end();
    }
  }

  Future<void> shareAiAnswerToSlack({
    required VocEntity voc,
    required String answer,
  }) async {
    if (!_connectors.slackNotifier.isConfigured) {
      _error = 'Slack Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _connectors.slackNotifier.shareAiAnswer(
        voc: voc,
        answer: answer,
      );
      _success = 'Slack 공유 완료';
    } catch (e) {
      _error = 'Slack 공유 실패: $e';
    } finally {
      _end();
    }
  }

  Future<String?> publishApprovedToConfluence({
    required VocEntity voc,
    required String approvedAnswer,
  }) async {
    _start();
    try {
      final pageUrl = await _connectors.confluencePublisher.publishApprovedAnswer(
        voc: voc,
        approvedAnswer: approvedAnswer,
      );
      _success = 'Confluence 문서화 완료';
      return pageUrl;
    } catch (e) {
      _error = 'Confluence 등록 실패: $e';
      return null;
    } finally {
      _end();
    }
  }

  void _start() {
    _isLoading = true;
    _error = null;
    _success = null;
    notifyListeners();
  }

  void _end() {
    _isLoading = false;
    notifyListeners();
  }
}
