import 'dart:convert';

import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';

import '../../core/constants/app_constants.dart';
import '../../core/database/database_helper.dart';
import '../../core/utils/vector_utils.dart';
import '../../core/utils/voc_category_catalog.dart';
import '../../domain/entities/voc_entity.dart';
import '../../domain/repositories/voc_repository.dart';
import '../../presentation/viewmodels/settings_viewmodel.dart';
import '../datasources/local/voc_local_datasource.dart';
import '../repositories/voc_repository_impl.dart';
import 'webhook_service.dart';

class PeerVocPullResult {
  const PeerVocPullResult({
    required this.remoteTotal,
    required this.created,
    required this.updated,
    required this.failedApps,
    required this.successApps,
  });

  final int remoteTotal;
  final int created;
  final int updated;
  final int failedApps;
  final int successApps;

  int get applied => created + updated;
}

class PeerBootstrapResult {
  const PeerBootstrapResult({
    required this.vocRemoteTotal,
    required this.vocCreated,
    required this.vocUpdated,
    required this.manualRemoteTotal,
    required this.manualCreated,
    required this.manualSkipped,
    required this.failedApps,
    required this.successApps,
  });

  final int vocRemoteTotal;
  final int vocCreated;
  final int vocUpdated;
  final int manualRemoteTotal;
  final int manualCreated;
  final int manualSkipped;
  final int failedApps;
  final int successApps;
}

class PeerSyncService {
  PeerSyncService(this.settings)
      : _vocRepository = VocRepositoryImpl(VocLocalDatasource(DatabaseHelper.instance));

  final SettingsViewModel settings;
  final VocRepository _vocRepository;
  final WebhookService _webhook = WebhookService();
  final Uuid _uuid = const Uuid();

  Future<PeerVocPullResult> pullAllVocs() async {
    final targets = settings.vocForwardWebhookTargets;
    if (targets.isEmpty) {
      throw StateError('가져올 대상 앱 URL이 없습니다.');
    }

    var remoteTotal = 0;
    var created = 0;
    var updated = 0;
    var failedApps = 0;
    var successApps = 0;

    for (final target in targets) {
      try {
        final payload = await _webhook.getJsonForMap(
          url: _toExportEndpoint(target),
          headers: _authHeaders(),
        );
        successApps += 1;
        final sourceApp = _sourceApp(payload);
        final snapshot = payload['snapshot'] is Map
            ? Map<String, dynamic>.from(payload['snapshot'] as Map)
            : const <String, dynamic>{};
        final vocs = (snapshot['vocs'] as List?) ?? const [];
        remoteTotal += vocs.length;

        for (final item in vocs) {
          if (item is! Map) continue;
          final row = Map<String, dynamic>.from(item);
          final result = await _upsertRemoteVoc(sourceApp, row, source: 'peer-pull');
          if (result) {
            created += 1;
          } else {
            updated += 1;
          }
        }
      } catch (_) {
        failedApps += 1;
      }
    }

    return PeerVocPullResult(
      remoteTotal: remoteTotal,
      created: created,
      updated: updated,
      failedApps: failedApps,
      successApps: successApps,
    );
  }

  Future<PeerBootstrapResult> bootstrap() async {
    final targets = settings.vocForwardWebhookTargets;
    if (targets.isEmpty) {
      throw StateError('초기 동기화 대상 앱이 없습니다.');
    }

    final db = await DatabaseHelper.instance.database;
    final existingManualRows = await db.query(
      AppConstants.tableKnowledgeBase,
      columns: ['question', 'answer'],
      where: 'category = ? OR project = ?',
      whereArgs: const ['시스템매뉴얼', 'manual-upload'],
    );
    final manualKeys = <String>{
      for (final row in existingManualRows)
        _manualKey(row['question']?.toString() ?? '', row['answer']?.toString() ?? ''),
    };

    var vocRemoteTotal = 0;
    var vocCreated = 0;
    var vocUpdated = 0;
    var manualRemoteTotal = 0;
    var manualCreated = 0;
    var manualSkipped = 0;
    var successApps = 0;
    var failedApps = 0;

    for (final target in targets) {
      try {
        final payload = await _webhook.getJsonForMap(
          url: _toExportEndpoint(target),
          headers: _authHeaders(),
        );
        successApps += 1;
        final sourceApp = _sourceApp(payload);
        final snapshot = payload['snapshot'] is Map
            ? Map<String, dynamic>.from(payload['snapshot'] as Map)
            : const <String, dynamic>{};
        final vocs = (snapshot['vocs'] as List?) ?? const [];
        final manuals = (snapshot['manuals'] as List?) ?? const [];
        vocRemoteTotal += vocs.length;
        manualRemoteTotal += manuals.length;

        for (final item in vocs) {
          if (item is! Map) continue;
          final row = Map<String, dynamic>.from(item);
          final wasCreated = await _upsertRemoteVoc(sourceApp, row, source: 'peer-bootstrap');
          if (wasCreated) {
            vocCreated += 1;
          } else {
            vocUpdated += 1;
          }
        }

        for (final item in manuals) {
          if (item is! Map) continue;
          final row = Map<String, dynamic>.from(item);
          final question = row['question']?.toString().trim() ?? '';
          final answer = row['answer']?.toString().trim() ?? '';
          if (question.isEmpty || answer.isEmpty) {
            manualSkipped += 1;
            continue;
          }
          final key = _manualKey(question, answer);
          if (manualKeys.contains(key)) {
            manualSkipped += 1;
            continue;
          }
          final now = DateTime.now();
          await db.insert(
            AppConstants.tableKnowledgeBase,
            {
              'id': _uuid.v4(),
              'question': question,
              'answer': answer,
              'category': row['category']?.toString() ?? '시스템매뉴얼',
              'customer': row['customer']?.toString(),
              'project': row['project']?.toString() ?? 'manual-upload',
              'voc_id': row['voc_id']?.toString(),
              'embedding': row['embedding']?.toString() ??
                  jsonEncode(VectorUtils.simpleTextEmbedding('$question $answer')),
              'resolved_at': row['resolved_at']?.toString() ?? now.toIso8601String(),
              'created_at': row['created_at']?.toString() ?? now.toIso8601String(),
            },
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
          manualKeys.add(key);
          manualCreated += 1;
        }
      } catch (_) {
        failedApps += 1;
      }
    }

    return PeerBootstrapResult(
      vocRemoteTotal: vocRemoteTotal,
      vocCreated: vocCreated,
      vocUpdated: vocUpdated,
      manualRemoteTotal: manualRemoteTotal,
      manualCreated: manualCreated,
      manualSkipped: manualSkipped,
      failedApps: failedApps,
      successApps: successApps,
    );
  }

  Future<bool> _upsertRemoteVoc(
    String sourceApp,
    Map<String, dynamic> row, {
    required String source,
  }) async {
    final remoteId = row['id']?.toString().trim() ?? '';
    final title = row['title']?.toString().trim().isNotEmpty == true
        ? row['title'].toString().trim()
        : '제목없음';
    final content = row['content']?.toString().trim().isNotEmpty == true
        ? row['content'].toString().trim()
        : '내용 없음';
    final stableRef = '$sourceApp:${remoteId.isEmpty ? _fallbackRemoteKey(title, content) : remoteId}';
    final db = await DatabaseHelper.instance.database;
    final existing = await db.query(
      AppConstants.tableVocs,
      columns: ['id'],
      where: 'source_ref = ?',
      whereArgs: [stableRef],
      limit: 1,
    );

    final now = DateTime.now();
    final createdAt = DateTime.tryParse(row['created_at']?.toString() ?? '') ?? now;
    final updatedAt = DateTime.tryParse(row['updated_at']?.toString() ?? '') ?? now;
    final localId = existing.isEmpty ? _uuid.v4() : existing.first['id'].toString();
    final normalizedCategory = VocCategoryCatalog.normalize(
      row['category']?.toString(),
      title: title,
      content: content,
      aiCategory: row['ai_category']?.toString(),
      tags: row['tags']?.toString(),
    );

    final voc = VocEntity(
      id: localId,
      title: title,
      content: content,
      category: normalizedCategory,
      tags: _optional(row['tags']),
      customer: _required(row['customer'], '미입력'),
      project: _required(row['project'], '미입력'),
      priority: _normalizePriority(row['priority']?.toString()),
      status: _normalizeStatus(row['status']?.toString()),
      aiCategory: _optional(row['ai_category']),
      urgency: _optional(row['urgency']),
      businessType: _optional(row['business_type']),
      department: _optional(row['department']),
      assignee: _optional(row['assignee']),
      source: source,
      sourceRef: stableRef,
      createdAt: createdAt,
      updatedAt: updatedAt,
    );

    if (existing.isEmpty) {
      await _vocRepository.createVoc(voc);
      return true;
    }
    await _vocRepository.updateVoc(voc);
    return false;
  }

  String _sourceApp(Map<String, dynamic> payload) {
    final value = payload['source_app']?.toString().trim() ?? '';
    return value.isEmpty ? 'unknown-app' : value;
  }

  Map<String, String> _authHeaders() {
    final token = settings.vocSyncBearerToken.trim();
    return token.isEmpty ? const {} : {'Authorization': 'Bearer $token'};
  }

  String _toExportEndpoint(String target) {
    var base = target.trim();
    if (base.endsWith('/webhook/voc')) {
      base = base.substring(0, base.length - '/webhook/voc'.length);
    } else if (base.endsWith('/voc')) {
      base = base.substring(0, base.length - '/voc'.length);
    } else if (base.endsWith('/webhook/sync/full')) {
      base = base.substring(0, base.length - '/webhook/sync/full'.length);
    }
    base = base.replaceAll(RegExp(r'/+$'), '');
    return '$base/webhook/sync/export';
  }

  String _normalizeStatus(String? raw) {
    final value = raw?.trim().toUpperCase() ?? '';
    return switch (value) {
      'IN_PROGRESS' => AppConstants.vocStatusInProgress,
      'RESOLVED' => AppConstants.vocStatusResolved,
      'REJECTED' => AppConstants.vocStatusRejected,
      _ => AppConstants.vocStatusOpen,
    };
  }

  String _normalizePriority(String? raw) {
    final value = raw?.trim().toUpperCase() ?? '';
    return switch (value) {
      'HIGH' => AppConstants.priorityHigh,
      'LOW' => AppConstants.priorityLow,
      _ => AppConstants.priorityMedium,
    };
  }

  String? _optional(dynamic value) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? null : text;
  }

  String _required(dynamic value, String fallback) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? fallback : text;
  }

  String _fallbackRemoteKey(String title, String content) =>
      '${title.trim().toLowerCase()}|${content.trim().toLowerCase()}';

  String _manualKey(String question, String answer) =>
      '${question.trim().toLowerCase()}|${answer.trim().toLowerCase()}';
}
