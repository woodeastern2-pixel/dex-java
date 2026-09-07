import 'dart:io';

import 'package:excel/excel.dart';

import '../../core/constants/app_constants.dart';
import '../../domain/entities/voc_entity.dart';

class ExcelService {
  Future<List<Map<String, String>>> importVocRows(String filePath) async {
    final bytes = await File(filePath).readAsBytes();
    final excel = Excel.decodeBytes(bytes);
    if (excel.tables.isEmpty) return [];

    final sheet = excel.tables.values.first;
    if (sheet == null || sheet.rows.isEmpty) return [];

    final header = sheet.rows.first
        .map((c) => c?.value?.toString().trim() ?? '')
        .toList();

    final rows = <Map<String, String>>[];
    for (int i = 1; i < sheet.rows.length; i++) {
      final row = sheet.rows[i];
      if (row.every((c) => c == null || (c.value?.toString().trim().isEmpty ?? true))) {
        continue;
      }

      final map = <String, String>{};
      for (int j = 0; j < header.length; j++) {
        final key = header[j];
        if (key.isEmpty) continue;
        final value = j < row.length ? (row[j]?.value?.toString() ?? '') : '';
        map[key] = value;
      }
      rows.add(map);
    }

    return rows;
  }

  Future<String> exportVocs({
    required String filePath,
    required List<VocEntity> vocs,
    required List<Map<String, dynamic>> responses,
  }) async {
    final excel = Excel.createExcel();
    final sheet = excel['VOC'];

    final header = [
      'id',
      'title',
      'content',
      'category',
      'customer',
      'project',
      'priority',
      'status',
      'urgency',
      'department',
      'assignee',
      'duplicate_score',
      'jira_required',
      'created_at',
    ];

    sheet.appendRow(header.map(TextCellValue.new).toList());

    for (final v in vocs) {
      sheet.appendRow([
        TextCellValue(v.id),
        TextCellValue(v.title),
        TextCellValue(v.content),
        TextCellValue(v.category),
        TextCellValue(v.customer),
        TextCellValue(v.project),
        TextCellValue(v.priority),
        TextCellValue(v.status),
        TextCellValue(v.urgency ?? ''),
        TextCellValue(v.department ?? ''),
        TextCellValue(v.assignee ?? ''),
        TextCellValue('${v.duplicateScore ?? 0}'),
        TextCellValue(v.jiraRequired ? 'Y' : 'N'),
        TextCellValue(v.createdAt.toIso8601String()),
      ]);
    }

    final responseSheet = excel['Responses'];
    responseSheet.appendRow([
      TextCellValue('id'),
      TextCellValue('voc_id'),
      TextCellValue('content'),
      TextCellValue('status'),
      TextCellValue('ai_generated'),
      TextCellValue('confidence_score'),
      TextCellValue('approved_by'),
      TextCellValue('created_at'),
    ]);

    for (final r in responses) {
      responseSheet.appendRow([
        TextCellValue((r['id'] ?? '').toString()),
        TextCellValue((r['voc_id'] ?? '').toString()),
        TextCellValue((r['content'] ?? '').toString()),
        TextCellValue((r['status'] ?? '').toString()),
        TextCellValue((r['ai_generated'] ?? '').toString()),
        TextCellValue((r['confidence_score'] ?? '').toString()),
        TextCellValue((r['approved_by'] ?? '').toString()),
        TextCellValue((r['created_at'] ?? '').toString()),
      ]);
    }

    final data = excel.encode();
    if (data == null) {
      throw Exception('엑셀 인코딩 실패');
    }
    await File(filePath).writeAsBytes(data);
    return filePath;
  }

  String normalizePriority(String raw) {
    final v = raw.trim().toUpperCase();
    if (v == 'HIGH' || v == 'H') return AppConstants.priorityHigh;
    if (v == 'LOW' || v == 'L') return AppConstants.priorityLow;
    return AppConstants.priorityMedium;
  }
}
