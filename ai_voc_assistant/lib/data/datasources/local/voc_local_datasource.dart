import 'dart:convert';
import '../../../core/constants/app_constants.dart';
import '../../../core/database/database_helper.dart';
import '../../../domain/entities/voc_entity.dart';
import '../../../domain/entities/response_entity.dart';

class VocLocalDatasource {
  final DatabaseHelper _dbHelper;

  VocLocalDatasource(this._dbHelper);

  Future<List<VocEntity>> getAllVocs() async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableVocs,
      orderBy: 'created_at DESC',
    );
    return maps.map(_mapToVoc).toList();
  }

  Future<VocEntity?> getVocById(String id) async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableVocs,
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return _mapToVoc(maps.first);
  }

  Future<List<VocEntity>> getVocsByStatus(String status) async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableVocs,
      where: 'status = ?',
      whereArgs: [status],
      orderBy: 'created_at DESC',
    );
    return maps.map(_mapToVoc).toList();
  }

  Future<List<VocEntity>> getVocsByCategory(String category) async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableVocs,
      where: 'category = ?',
      whereArgs: [category],
      orderBy: 'created_at DESC',
    );
    return maps.map(_mapToVoc).toList();
  }

  Future<List<VocEntity>> searchVocs(String query) async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableVocs,
      where: 'title LIKE ? OR content LIKE ? OR customer LIKE ? OR project LIKE ?',
      whereArgs: ['%$query%', '%$query%', '%$query%', '%$query%'],
      orderBy: 'created_at DESC',
    );
    return maps.map(_mapToVoc).toList();
  }

  Future<VocEntity> insertVoc(VocEntity voc) async {
    final db = await _dbHelper.database;
    await db.insert(AppConstants.tableVocs, _vocToMap(voc));
    return voc;
  }

  Future<VocEntity> updateVoc(VocEntity voc) async {
    final db = await _dbHelper.database;
    await db.update(
      AppConstants.tableVocs,
      _vocToMap(voc),
      where: 'id = ?',
      whereArgs: [voc.id],
    );
    return voc;
  }

  Future<void> deleteVoc(String id) async {
    final db = await _dbHelper.database;
    await db.delete(AppConstants.tableVocs, where: 'id = ?', whereArgs: [id]);
  }

  Future<Map<String, int>> getVocCountByStatus() async {
    final db = await _dbHelper.database;
    final result = await db.rawQuery(
      'SELECT status, COUNT(*) as count FROM ${AppConstants.tableVocs} GROUP BY status',
    );
    return {for (final row in result) row['status'] as String: row['count'] as int};
  }

  Future<Map<String, int>> getVocCountByCategory() async {
    final db = await _dbHelper.database;
    final result = await db.rawQuery(
      'SELECT category, COUNT(*) as count FROM ${AppConstants.tableVocs} GROUP BY category',
    );
    return {for (final row in result) row['category'] as String: row['count'] as int};
  }

  Future<List<Map<String, dynamic>>> getMonthlyStats() async {
    final db = await _dbHelper.database;
    return await db.rawQuery('''
      SELECT 
        strftime('%Y-%m', created_at) as month,
        COUNT(*) as total,
        SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END) as resolved
      FROM ${AppConstants.tableVocs}
      WHERE created_at >= date('now', '-12 months')
      GROUP BY month
      ORDER BY month ASC
    ''');
  }

  Future<Map<String, dynamic>> getAdvancedMetrics() async {
    final db = await _dbHelper.database;

    final totalVocQ = await db.rawQuery(
      'SELECT COUNT(*) as c FROM ${AppConstants.tableVocs}',
    );
    final total = (totalVocQ.first['c'] as int?) ?? 0;

    final dupQ = await db.rawQuery(
      'SELECT COUNT(*) as c FROM ${AppConstants.tableVocs} WHERE duplicate_score >= 0.85',
    );
    final duplicateCount = (dupQ.first['c'] as int?) ?? 0;

    final aiRespQ = await db.rawQuery(
      'SELECT COUNT(*) as c FROM ${AppConstants.tableResponses} WHERE ai_generated = 1',
    );
    final totalRespQ = await db.rawQuery(
      'SELECT COUNT(*) as c FROM ${AppConstants.tableResponses}',
    );
    final aiResponses = (aiRespQ.first['c'] as int?) ?? 0;
    final totalResponses = (totalRespQ.first['c'] as int?) ?? 0;

    final avgProcessQ = await db.rawQuery('''
      SELECT AVG(
        (julianday(updated_at) - julianday(created_at)) * 24 * 60
      ) as avg_minutes
      FROM ${AppConstants.tableVocs}
      WHERE status = 'RESOLVED'
    ''');
    final avgMinutes = (avgProcessQ.first['avg_minutes'] as num?)?.toDouble() ?? 0.0;

    final monthlyDup = await db.rawQuery('''
      SELECT strftime('%Y-%m', created_at) as month,
             COUNT(*) as total,
             SUM(CASE WHEN duplicate_score >= 0.85 THEN 1 ELSE 0 END) as dup
      FROM ${AppConstants.tableVocs}
      WHERE created_at >= date('now', '-6 months')
      GROUP BY month
      ORDER BY month ASC
    ''');

    double duplicateReductionRate = 0.0;
    if (monthlyDup.length >= 2) {
      final first = monthlyDup.first;
      final last = monthlyDup.last;
      final firstRate = ((first['dup'] as int?) ?? 0) /
          ((((first['total'] as int?) ?? 0) == 0) ? 1 : (first['total'] as int));
      final lastRate = ((last['dup'] as int?) ?? 0) /
          ((((last['total'] as int?) ?? 0) == 0) ? 1 : (last['total'] as int));
      duplicateReductionRate = (firstRate - lastRate).clamp(-1.0, 1.0);
    }

    return {
      'duplicateCount': duplicateCount,
      'duplicateRate': total == 0 ? 0.0 : duplicateCount / total,
      'duplicateReductionRate': duplicateReductionRate,
      'aiUsageRate': totalResponses == 0 ? 0.0 : aiResponses / totalResponses,
      'avgProcessMinutes': avgMinutes,
      'totalResponses': totalResponses,
    };
  }

  Future<List<Map<String, dynamic>>> getTopAssigneeStats({int topN = 3}) async {
    final db = await _dbHelper.database;
    return db.rawQuery('''
      SELECT assignee,
             COUNT(*) as handled,
             AVG(COALESCE(assignee_score, 0)) as accuracy
      FROM ${AppConstants.tableVocs}
      WHERE assignee IS NOT NULL AND assignee != ''
      GROUP BY assignee
      ORDER BY handled DESC
      LIMIT ?
    ''', [topN]);
  }

  // Responses
  Future<List<ResponseEntity>> getResponsesByVocId(String vocId) async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableResponses,
      where: 'voc_id = ?',
      whereArgs: [vocId],
      orderBy: 'created_at DESC',
    );
    return maps.map(_mapToResponse).toList();
  }

  Future<ResponseEntity> insertResponse(ResponseEntity response) async {
    final db = await _dbHelper.database;
    await db.insert(AppConstants.tableResponses, _responseToMap(response));
    return response;
  }

  Future<ResponseEntity> updateResponse(ResponseEntity response) async {
    final db = await _dbHelper.database;
    await db.update(
      AppConstants.tableResponses,
      _responseToMap(response),
      where: 'id = ?',
      whereArgs: [response.id],
    );
    return response;
  }

  Future<void> deleteResponse(String id) async {
    final db = await _dbHelper.database;
    await db.delete(AppConstants.tableResponses, where: 'id = ?', whereArgs: [id]);
  }

  // Mappers
  VocEntity _mapToVoc(Map<String, dynamic> map) {
    List<double>? embedding;
    final embJson = map['embedding'] as String?;
    if (embJson != null && embJson.isNotEmpty) {
      embedding = List<double>.from(jsonDecode(embJson));
    }
    return VocEntity(
      id: map['id'] as String,
      title: map['title'] as String,
      content: map['content'] as String,
      category: map['category'] as String,
      tags: map['tags'] as String?,
      customer: map['customer'] as String,
      project: map['project'] as String,
      priority: map['priority'] as String,
      status: map['status'] as String,
      aiCategory: map['ai_category'] as String?,
      isBusinessRelated: (map['is_business_related'] as int) == 1,
      businessScore: (map['business_score'] as num?)?.toDouble(),
      categoryScore: (map['category_score'] as num?)?.toDouble(),
      urgency: map['urgency'] as String?,
      urgencyScore: (map['urgency_score'] as num?)?.toDouble(),
      businessType: map['business_type'] as String?,
      department: map['department'] as String?,
      departmentScore: (map['department_score'] as num?)?.toDouble(),
      assignee: map['assignee'] as String?,
      assigneeScore: (map['assignee_score'] as num?)?.toDouble(),
      duplicateOfVocId: map['duplicate_of_voc_id'] as String?,
      duplicateScore: (map['duplicate_score'] as num?)?.toDouble(),
      jiraRequired: (map['jira_required'] as int? ?? 0) == 1,
      jiraScore: (map['jira_score'] as num?)?.toDouble(),
      analysisReason: map['analysis_reason'] as String?,
      embedding: embedding,
      source: map['source'] as String?,
      sourceRef: map['source_ref'] as String?,
      processingMinutes: map['processing_minutes'] as int?,
      createdAt: DateTime.parse(map['created_at'] as String),
      updatedAt: DateTime.parse(map['updated_at'] as String),
    );
  }

  Map<String, dynamic> _vocToMap(VocEntity voc) {
    return {
      'id': voc.id,
      'title': voc.title,
      'content': voc.content,
      'category': voc.category,
      'tags': voc.tags,
      'customer': voc.customer,
      'project': voc.project,
      'priority': voc.priority,
      'status': voc.status,
      'ai_category': voc.aiCategory,
      'is_business_related': voc.isBusinessRelated ? 1 : 0,
      'business_score': voc.businessScore,
      'category_score': voc.categoryScore,
      'urgency': voc.urgency,
      'urgency_score': voc.urgencyScore,
      'business_type': voc.businessType,
      'department': voc.department,
      'department_score': voc.departmentScore,
      'assignee': voc.assignee,
      'assignee_score': voc.assigneeScore,
      'duplicate_of_voc_id': voc.duplicateOfVocId,
      'duplicate_score': voc.duplicateScore,
      'jira_required': voc.jiraRequired ? 1 : 0,
      'jira_score': voc.jiraScore,
      'analysis_reason': voc.analysisReason,
      'embedding': voc.embedding == null ? null : jsonEncode(voc.embedding),
      'source': voc.source,
      'source_ref': voc.sourceRef,
      'processing_minutes': voc.processingMinutes,
      'created_at': voc.createdAt.toIso8601String(),
      'updated_at': voc.updatedAt.toIso8601String(),
    };
  }

  ResponseEntity _mapToResponse(Map<String, dynamic> map) {
    List<String> refs = [];
    final refsJson = map['referenced_voc_ids'];
    if (refsJson != null && (refsJson as String).isNotEmpty) {
      refs = List<String>.from(jsonDecode(refsJson));
    }
    return ResponseEntity(
      id: map['id'] as String,
      vocId: map['voc_id'] as String,
      content: map['content'] as String,
      status: map['status'] as String,
      aiGenerated: (map['ai_generated'] as int) == 1,
      confidenceScore: map['confidence_score'] as double?,
      referencedVocIds: refs,
      approvedBy: map['approved_by'] as String?,
      approvedAt: map['approved_at'] != null
          ? DateTime.parse(map['approved_at'] as String)
          : null,
        adoptionCount: map['adoption_count'] as int? ?? 0,
        usageCount: map['usage_count'] as int? ?? 0,
        lastUsedAt: map['last_used_at'] != null
          ? DateTime.parse(map['last_used_at'] as String)
          : null,
      createdAt: DateTime.parse(map['created_at'] as String),
      updatedAt: DateTime.parse(map['updated_at'] as String),
    );
  }

  Map<String, dynamic> _responseToMap(ResponseEntity r) {
    return {
      'id': r.id,
      'voc_id': r.vocId,
      'content': r.content,
      'status': r.status,
      'ai_generated': r.aiGenerated ? 1 : 0,
      'confidence_score': r.confidenceScore,
      'referenced_voc_ids': jsonEncode(r.referencedVocIds),
      'approved_by': r.approvedBy,
      'approved_at': r.approvedAt?.toIso8601String(),
      'adoption_count': r.adoptionCount,
      'usage_count': r.usageCount,
      'last_used_at': r.lastUsedAt?.toIso8601String(),
      'created_at': r.createdAt.toIso8601String(),
      'updated_at': r.updatedAt.toIso8601String(),
    };
  }
}
