import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../../core/constants/app_constants.dart';
import '../../core/database/database_helper.dart';
import '../../core/utils/vector_utils.dart';
import '../../domain/entities/voc_entity.dart';
import '../../domain/entities/response_entity.dart';
import '../../domain/repositories/voc_repository.dart';

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
    required String priority,
  }) async {
    final now = DateTime.now();
    final voc = VocEntity(
      id: _uuid.v4(),
      title: title,
      content: content,
      category: category,
      tags: tags,
      customer: customer?.trim().isEmpty == true ? '미입력' : (customer?.trim().isNotEmpty == true ? customer!.trim() : '미입력'),
      project: project?.trim().isEmpty == true ? '미입력' : (project?.trim().isNotEmpty == true ? project!.trim() : '미입력'),
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
      category: category,
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
}
