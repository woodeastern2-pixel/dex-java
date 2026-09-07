import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../../core/constants/app_constants.dart';
import '../../core/database/database_helper.dart';
import '../../data/services/confluence_service.dart';
import '../../data/services/excel_service.dart';
import '../../data/services/outlook_service.dart';
import '../../data/services/webhook_service.dart';
import '../../domain/entities/voc_entity.dart';
import '../../domain/repositories/voc_repository.dart';
import 'settings_viewmodel.dart';

class IntegrationViewModel extends ChangeNotifier {
  final VocRepository _vocRepository;
  final SettingsViewModel _settingsViewModel;
  final _uuid = const Uuid();

  final _excel = ExcelService();
  final _outlook = OutlookService();
  final _webhook = WebhookService();

  bool _isLoading = false;
  String? _error;
  String? _success;

  IntegrationViewModel(this._vocRepository, this._settingsViewModel);

  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get success => _success;

  void clearMessages() {
    _error = null;
    _success = null;
    notifyListeners();
  }

  Future<int> importVocFromExcel(String filePath) async {
    _start();
    try {
      final rows = await _excel.importVocRows(filePath);
      int imported = 0;
      for (final row in rows) {
        final title = (row['title'] ?? row['VOC 제목'] ?? '').trim();
        final content = (row['content'] ?? row['VOC 내용'] ?? '').trim();
        if (title.isEmpty || content.isEmpty) continue;

        final now = DateTime.now();
        final voc = VocEntity(
          id: _uuid.v4(),
          title: title,
          content: content,
          category: (row['category'] ?? row['카테고리'] ?? '기능문의').trim(),
          customer: (row['customer'] ?? row['고객명'] ?? '미상').trim(),
          project: (row['project'] ?? row['프로젝트명'] ?? '미상').trim(),
          priority: _excel.normalizePriority(
            row['priority'] ?? row['우선순위'] ?? 'MEDIUM',
          ),
          status: AppConstants.vocStatusOpen,
          source: 'excel',
          sourceRef: filePath,
          createdAt: now,
          updatedAt: now,
        );
        await _vocRepository.createVoc(voc);
        imported += 1;
      }
      _success = 'VOC $imported건을 엑셀에서 가져왔습니다.';
      return imported;
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

  Future<int> collectOutlookAndCreateVoc({int top = 20}) async {
    _start();
    try {
      final token = _settingsViewModel.settings[AppConstants.settingOutlookAccessToken] ?? '';
      final folder = _settingsViewModel.settings[AppConstants.settingOutlookFolder] ?? 'Inbox';
      if (token.isEmpty) {
        throw Exception('Outlook Access Token이 설정되지 않았습니다.');
      }

      final mails = await _outlook.collectMails(
        accessToken: token,
        folder: folder,
        top: top,
      );

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
          final savedPath = await _outlook.saveAttachment(att);
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
    final webhook = _settingsViewModel.settings[AppConstants.settingTeamsWebhook] ?? '';
    if (webhook.isEmpty) {
      _error = 'Teams Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _webhook.sendTeamsAlert(
        webhookUrl: webhook,
        title: '[긴급 VOC] ${voc.title}',
        message: voc.content,
        extra: {
          '고객': voc.customer,
          '프로젝트': voc.project,
          '긴급도': voc.urgency ?? '-',
          '담당부서': voc.department ?? '-',
          '담당자': voc.assignee ?? '-',
        },
      );
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
    final webhook = _settingsViewModel.settings[AppConstants.settingTeamsWebhook] ?? '';
    if (webhook.isEmpty) {
      _error = 'Teams Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _webhook.sendTeamsAlert(
        webhookUrl: webhook,
        title: '[AI 답변 공유] ${voc.title}',
        message: answer,
        extra: {
          '고객': voc.customer,
          '카테고리': voc.category,
          '담당자 추천': voc.assignee ?? '-',
        },
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
    final webhook = _settingsViewModel.settings[AppConstants.settingSlackWebhook] ?? '';
    if (webhook.isEmpty) {
      _error = 'Slack Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _webhook.sendSlackMessage(
        webhookUrl: webhook,
        text: '[VOC 공유] ${voc.title}\n${voc.content}',
        fields: {
          '고객': voc.customer,
          '프로젝트': voc.project,
          '카테고리': voc.category,
          '긴급도': voc.urgency ?? '-',
        },
      );
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
    final webhook = _settingsViewModel.settings[AppConstants.settingSlackWebhook] ?? '';
    if (webhook.isEmpty) {
      _error = 'Slack Webhook이 설정되지 않았습니다.';
      notifyListeners();
      return;
    }

    _start();
    try {
      await _webhook.sendSlackMessage(
        webhookUrl: webhook,
        text: '[VOC 공유] ${voc.title}\n\nAI 추천 답변:\n$answer',
        fields: {
          '고객': voc.customer,
          '카테고리': voc.category,
          '긴급도': voc.urgency ?? '-',
          '담당자 추천': voc.assignee ?? '-',
        },
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
      final url = _settingsViewModel.settings[AppConstants.settingConfluenceUrl] ?? '';
      final space = _settingsViewModel.settings[AppConstants.settingConfluenceSpace] ?? '';
      final email = _settingsViewModel.settings[AppConstants.settingConfluenceEmail] ?? '';
      final token = _settingsViewModel.settings[AppConstants.settingConfluenceToken] ?? '';

      if (url.isEmpty || space.isEmpty || email.isEmpty || token.isEmpty) {
        throw Exception('Confluence 설정이 미완성입니다.');
      }

      final service = ConfluenceService(
        baseUrl: url,
        email: email,
        token: token,
        spaceKey: space,
      );

      final pageUrl = await service.createFaqPage(
        title: '[VOC FAQ] ${voc.title}',
        question: voc.content,
        answer: approvedAnswer,
        category: voc.category,
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
