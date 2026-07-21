import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../../core/constants/app_constants.dart';
import '../../core/database/database_helper.dart';
import '../../core/utils/voc_category_catalog.dart';
import '../../core/utils/vector_utils.dart';
import '../../domain/entities/voc_entity.dart';
import '../../domain/entities/response_entity.dart';
import '../../domain/repositories/voc_repository.dart';
import '../../data/services/ai_service.dart';

class VocViewModel extends ChangeNotifier {
  final VocRepository _repository;
  final _uuid = const Uuid();

  List<VocEntity> _vocs = [];
  VocEntity? _selectedVoc;
  List<ResponseEntity> _responses = [];
  bool _isLoading = false;
  String? _error;
  String _searchQuery = '';
  String _filterStatus = '';
  String _filterCategory = '';
  bool _isBulkAutoClassifying = false;
  bool _isBulkRecategorizing = false;

  VocViewModel(this._repository) {
    loadVocs();
  }

  List<VocEntity> get vocs => _filteredVocs;
  List<VocEntity> get allVocs => _vocs;
  VocEntity? get selectedVoc => _selectedVoc;
  List<ResponseEntity> get responses => _responses;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String get searchQuery => _searchQuery;
  String get filterStatus => _filterStatus;
  String get filterCategory => _filterCategory;
  bool get isBulkRecategorizing => _isBulkRecategorizing;

  List<VocEntity> get _filteredVocs {
    var list = _vocs;
    if (_searchQuery.isNotEmpty) {
      final q = _searchQuery.toLowerCase();
      list = list.where((v) =>
          v.title.toLowerCase().contains(q) ||
          v.content.toLowerCase().contains(q) ||
          v.customer.toLowerCase().contains(q) ||
          v.project.toLowerCase().contains(q)).toList();
    }
    if (_filterStatus.isNotEmpty) {
      list = list.where((v) => v.status == _filterStatus).toList();
    }
    if (_filterCategory.isNotEmpty) {
      list = list.where((v) => v.category == _filterCategory).toList();
    }
    return list;
  }

  Future<void> loadVocs() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      _vocs = await _repository.getAllVocs();
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> selectVoc(String id) async {
    _selectedVoc = _vocs.firstWhere((v) => v.id == id, orElse: () => _vocs.first);
    await loadResponsesForVoc(id);
    notifyListeners();
  }

  Future<void> loadResponsesForVoc(String vocId) async {
    _responses = await _repository.getResponsesByVocId(vocId);
    notifyListeners();
  }

  Future<VocEntity> createVoc({
    required String title,
    required String content,
    required String category,
    String? tags,
    String? customer,
    String? project,
    String? businessType,
    required String priority,
  }) async {
    final now = DateTime.now();
    final normalizedCategory = VocCategoryCatalog.normalize(
      category,
      title: title,
      content: content,
      tags: tags,
    );
    final voc = VocEntity(
      id: _uuid.v4(),
      title: title,
      content: content,
      category: normalizedCategory,
      tags: tags,
      customer: customer?.trim().isEmpty == true ? '미입력' : (customer?.trim().isNotEmpty == true ? customer!.trim() : '미입력'),
      project: project?.trim().isEmpty == true ? '미입력' : (project?.trim().isNotEmpty == true ? project!.trim() : '미입력'),
      businessType: businessType?.trim().isNotEmpty == true ? businessType!.trim() : null,
      priority: priority,
      status: AppConstants.vocStatusOpen,
      embedding: VectorUtils.simpleTextEmbedding('$title $content'),
      createdAt: now,
      updatedAt: now,
    );
    final created = await _repository.createVoc(voc);
    _vocs.insert(0, created);
    notifyListeners();
    return created;
  }

  Future<int> autoClassifyAllVocs({
    required Future<VocIntelligenceResult?> Function(String title, String content)
        analyzer,
    bool forceReanalyze = true,
  }) async {
    if (_isBulkAutoClassifying) return 0;

    _isBulkAutoClassifying = true;
    var updatedCount = 0;
    try {
      final allVocs = await _repository.getAllVocs();
      for (final voc in allVocs) {
        if (!forceReanalyze && voc.aiCategory?.trim().isNotEmpty == true) {
          continue;
        }

        final intelligence = await analyzer(voc.title, voc.content);
        if (intelligence == null) {
          continue;
        }

        final updated = voc.copyWith(
          category: VocCategoryCatalog.normalize(
            intelligence.category,
            title: voc.title,
            content: voc.content,
            aiCategory: intelligence.category,
            tags: voc.tags,
          ),
          tags: _buildAutoTags(voc, intelligence),
          priority: _priorityFromUrgency(intelligence.urgency),
          isBusinessRelated: intelligence.isBusiness,
          aiCategory: intelligence.category,
          businessScore: intelligence.businessScore,
          categoryScore: intelligence.categoryScore,
          urgency: intelligence.urgency,
          urgencyScore: intelligence.urgencyScore,
          department: intelligence.department,
          departmentScore: intelligence.departmentScore,
          assignee: intelligence.assignee,
          assigneeScore: intelligence.assigneeScore,
          duplicateOfVocId: intelligence.duplicateOfVocId,
          duplicateScore: intelligence.duplicateScore,
          jiraRequired: intelligence.jiraRequired,
          jiraScore: intelligence.jiraScore,
          analysisReason: intelligence.reason,
          status: intelligence.isBusiness
              ? voc.status
              : AppConstants.vocStatusRejected,
          updatedAt: DateTime.now(),
        );
        await _repository.updateVoc(updated);
        updatedCount++;
      }

      if (updatedCount > 0) {
        _vocs = await _repository.getAllVocs();
        notifyListeners();
      }
      return updatedCount;
    } finally {
      _isBulkAutoClassifying = false;
    }
  }

  Future<void> updateVocStatus(String id, String status) async {
    final idx = _vocs.indexWhere((v) => v.id == id);
    if (idx == -1) return;
    final updated = _vocs[idx].copyWith(
      status: status,
      updatedAt: DateTime.now(),
    );
    await _repository.updateVoc(updated);
    _vocs[idx] = updated;
    if (_selectedVoc?.id == id) _selectedVoc = updated;
    notifyListeners();
  }

  Future<void> updateVocFields(
    String id, {
    required String title,
    required String content,
    required String category,
    String? tags,
    String? customer,
    String? project,
    required String priority,
  }) async {
    final idx = _vocs.indexWhere((v) => v.id == id);
    if (idx == -1) return;

    final updated = _vocs[idx].copyWith(
      title: title,
      content: content,
      category: VocCategoryCatalog.normalize(
        category,
        title: title,
        content: content,
        tags: tags,
      ),
      tags: tags,
      customer: customer?.trim().isEmpty == true ? '미입력' : (customer?.trim().isNotEmpty == true ? customer!.trim() : '미입력'),
      project: project?.trim().isEmpty == true ? '미입력' : (project?.trim().isNotEmpty == true ? project!.trim() : '미입력'),
      priority: priority,
      updatedAt: DateTime.now(),
    );

    await _repository.updateVoc(updated);
    _vocs[idx] = updated;
    if (_selectedVoc?.id == id) _selectedVoc = updated;
    notifyListeners();
  }

  Future<int> reassignAllVocCategories() async {
    if (_isBulkRecategorizing) return 0;

    _isBulkRecategorizing = true;
    notifyListeners();

    try {
      final updatedCount = await _repository.reassignAllVocCategories();
      _vocs = await _repository.getAllVocs();
      if (_selectedVoc != null) {
        for (final voc in _vocs) {
          if (voc.id == _selectedVoc!.id) {
            _selectedVoc = voc;
            break;
          }
        }
      }
      notifyListeners();
      return updatedCount;
    } finally {
      _isBulkRecategorizing = false;
      notifyListeners();
    }
  }

  Future<int> importSampleVocs(List<VocEntity> samples) async {
    final existingIds = _vocs.map((v) => v.id).toSet();
    var createdCount = 0;

    for (final sample in samples) {
      if (existingIds.contains(sample.id)) {
        continue;
      }
      await _repository.createVoc(sample);
      _vocs.insert(0, sample);
      existingIds.add(sample.id);
      createdCount++;
    }

    if (createdCount > 0) {
      notifyListeners();
    }
    return createdCount;
  }

  Future<void> updateVocWithAiAnalysis(
    String id, {
    required bool isBusinessRelated,
    required String aiCategory,
    double? businessScore,
    double? categoryScore,
    String? urgency,
    double? urgencyScore,
    String? department,
    double? departmentScore,
    String? assignee,
    double? assigneeScore,
    String? duplicateOfVocId,
    double? duplicateScore,
    bool? jiraRequired,
    double? jiraScore,
    String? analysisReason,
  }) async {
    final idx = _vocs.indexWhere((v) => v.id == id);
    if (idx == -1) return;
    final updated = _vocs[idx].copyWith(
      isBusinessRelated: isBusinessRelated,
      aiCategory: aiCategory,
      businessScore: businessScore,
      categoryScore: categoryScore,
      urgency: urgency,
      urgencyScore: urgencyScore,
      department: department,
      departmentScore: departmentScore,
      assignee: assignee,
      assigneeScore: assigneeScore,
      duplicateOfVocId: duplicateOfVocId,
      duplicateScore: duplicateScore,
      jiraRequired: jiraRequired,
      jiraScore: jiraScore,
      analysisReason: analysisReason,
      status: isBusinessRelated ? null : AppConstants.vocStatusRejected,
      updatedAt: DateTime.now(),
    );
    await _repository.updateVoc(updated);
    _vocs[idx] = updated;
    if (_selectedVoc?.id == id) _selectedVoc = updated;
    notifyListeners();
  }

  Future<void> deleteVoc(String id) async {
    await _repository.deleteVoc(id);
    _vocs.removeWhere((v) => v.id == id);
    if (_selectedVoc?.id == id) _selectedVoc = null;
    notifyListeners();
  }

  Future<ResponseEntity> createDraftResponse({
    required String vocId,
    required String content,
    bool aiGenerated = false,
    double? confidence,
    List<String>? referencedVocIds,
  }) async {
    final now = DateTime.now();
    final response = ResponseEntity(
      id: _uuid.v4(),
      vocId: vocId,
      content: content,
      status: AppConstants.responseDraft,
      aiGenerated: aiGenerated,
      confidenceScore: confidence,
      referencedVocIds: referencedVocIds ?? [],
      createdAt: now,
      updatedAt: now,
    );
    final created = await _repository.createResponse(response);
    _responses.insert(0, created);
    notifyListeners();
    return created;
  }

  Future<ResponseEntity?> adoptAiAnswer({
    required String vocId,
    required String content,
    double? confidence,
    List<String>? referencedVocIds,
    String? responseId,
  }) async {
    final now = DateTime.now();
    final existingIndex = responseId == null
        ? -1
        : _responses.indexWhere((r) => r.id == responseId);

    final response = existingIndex >= 0
        ? _responses[existingIndex].copyWith(
            content: content,
            status: AppConstants.responseApproved,
            confidenceScore: confidence,
            referencedVocIds: referencedVocIds ?? _responses[existingIndex].referencedVocIds,
            approvedBy: 'AI 채택',
            approvedAt: now,
            adoptionCount: _responses[existingIndex].adoptionCount + 1,
            usageCount: _responses[existingIndex].usageCount + 1,
            lastUsedAt: now,
            updatedAt: now,
          )
        : ResponseEntity(
            id: _uuid.v4(),
            vocId: vocId,
            content: content,
            status: AppConstants.responseApproved,
            aiGenerated: true,
            confidenceScore: confidence,
            referencedVocIds: referencedVocIds ?? const [],
            approvedBy: 'AI 채택',
            approvedAt: now,
            adoptionCount: 1,
            usageCount: 1,
            lastUsedAt: now,
            createdAt: now,
            updatedAt: now,
          );

    if (existingIndex >= 0) {
      await _repository.updateResponse(response);
      _responses[existingIndex] = response;
    } else {
      final created = await _repository.createResponse(response);
      _responses.insert(0, created);
    }

    notifyListeners();
    return response;
  }

  Future<void> recordAiFeedback({
    required String vocId,
    required String feedbackType,
    String? responseId,
    String? note,
  }) async {
    final db = await DatabaseHelper.instance.database;
    await db.insert(
      'ai_feedback',
      {
        'id': _uuid.v4(),
        'voc_id': vocId,
        'response_id': responseId,
        'feedback_type': feedbackType,
        'note': note,
        'created_at': DateTime.now().toIso8601String(),
      },
    );
  }

  Future<void> approveResponse(String responseId, String approvedBy) async {
    final idx = _responses.indexWhere((r) => r.id == responseId);
    if (idx == -1) return;
    final updated = _responses[idx].copyWith(
      status: AppConstants.responseApproved,
      approvedBy: approvedBy,
      approvedAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );
    await _repository.updateResponse(updated);
    _responses[idx] = updated;
    notifyListeners();
  }

  Future<ResponseEntity?> updateResponseContent({
    required String responseId,
    required String content,
  }) async {
    final idx = _responses.indexWhere((r) => r.id == responseId);
    if (idx == -1) return null;
    final updated = _responses[idx].copyWith(
      content: content,
      updatedAt: DateTime.now(),
    );
    await _repository.updateResponse(updated);
    _responses[idx] = updated;
    notifyListeners();
    return updated;
  }

  void setSearch(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  void setFilterStatus(String status) {
    _filterStatus = status;
    notifyListeners();
  }

  void setFilterCategory(String category) {
    _filterCategory = category;
    notifyListeners();
  }

  void clearFilters() {
    _searchQuery = '';
    _filterStatus = '';
    _filterCategory = '';
    notifyListeners();
  }

  String _priorityFromUrgency(String urgency) {
    final normalized = urgency.trim().toLowerCase();
    if (normalized.contains('high') ||
        normalized.contains('critical') ||
        normalized.contains('긴급')) {
      return AppConstants.priorityHigh;
    }
    if (normalized.contains('low') || normalized.contains('낮')) {
      return AppConstants.priorityLow;
    }
    return AppConstants.priorityMedium;
  }

  String _buildAutoTags(VocEntity voc, VocIntelligenceResult intelligence) {
    final values = <String>{
      intelligence.category.trim(),
      intelligence.urgency.trim(),
      intelligence.department.trim(),
      if (voc.businessType?.trim().isNotEmpty == true) voc.businessType!.trim(),
      if (voc.source?.trim().isNotEmpty == true) voc.source!.trim(),
    };
    values.removeWhere((item) => item.isEmpty);
    return values.join(', ');
  }
}
