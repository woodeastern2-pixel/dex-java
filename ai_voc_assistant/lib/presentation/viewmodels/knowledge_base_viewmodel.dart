import 'package:flutter/foundation.dart';
import '../../domain/entities/knowledge_base_entity.dart';
import '../../domain/repositories/knowledge_base_repository.dart';
import '../../data/services/manual_document_import_service.dart';

class KnowledgeBaseViewModel extends ChangeNotifier {
  final KnowledgeBaseRepository _repository;
  late final ManualDocumentImportService _manualImportService;

  List<KnowledgeBaseEntity> _entries = [];
  bool _isLoading = false;
  bool _isImportingManual = false;
  String? _error;
  String _filterCategory = '';
  String _searchQuery = '';
  static const String _manualCategory = ManualDocumentImportService.manualCategory;
  static const String _manualProjectMarker = 'manual-upload';

  KnowledgeBaseViewModel(this._repository) {
    _manualImportService = ManualDocumentImportService(_repository);
    loadEntries();
  }

  List<KnowledgeBaseEntity> get entries => _filtered;
  bool get isLoading => _isLoading;
  bool get isImportingManual => _isImportingManual;
  String? get error => _error;
  String get filterCategory => _filterCategory;
  String get searchQuery => _searchQuery;

  List<KnowledgeBaseEntity> get _filtered {
    var list = _entries;
    if (_filterCategory.isNotEmpty) {
      list = list.where((e) => e.category == _filterCategory).toList();
    }
    if (_searchQuery.isNotEmpty) {
      final q = _searchQuery.toLowerCase();
      list = list
          .where((e) =>
              e.question.toLowerCase().contains(q) ||
              e.answer.toLowerCase().contains(q))
          .toList();
    }
    return list;
  }

  Future<void> loadEntries() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      _entries = await _repository.getAllEntries();
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> deleteEntry(String id) async {
    await _repository.deleteEntry(id);
    _entries.removeWhere((e) => e.id == id);
    notifyListeners();
  }

  Future<ManualImportResult?> importManualDocuments(List<String> filePaths) async {
    final normalized = filePaths
        .map((path) => path.trim())
        .where((path) => path.isNotEmpty)
        .toList();
    if (normalized.isEmpty) {
      return null;
    }

    _isImportingManual = true;
    _error = null;
    notifyListeners();

    try {
      final result = await _manualImportService.importDocuments(normalized);
      _entries = await _repository.getAllEntries();
      return result;
    } catch (e) {
      _error = e.toString();
      return null;
    } finally {
      _isImportingManual = false;
      notifyListeners();
    }
  }

  bool isSupportedManualFile(String fileName) {
    return _manualImportService.isSupported(fileName);
  }

  Map<String, int> get manualEntriesByFile {
    final grouped = <String, int>{};
    for (final entry in _entries) {
      final isManual = entry.category == _manualCategory &&
          (entry.project == _manualProjectMarker ||
              (entry.question.contains('[') &&
                  entry.question.contains('매뉴얼 섹션')));
      if (!isManual) continue;
      final fileName = (entry.customer ?? '').trim();
      if (fileName.isEmpty) continue;
      grouped[fileName] = (grouped[fileName] ?? 0) + 1;
    }

    final sortedKeys = grouped.keys.toList()..sort();
    return {
      for (final key in sortedKeys) key: grouped[key]!,
    };
  }

  Future<int> deleteManualEntriesByFile(String fileName) async {
    final target = fileName.trim();
    if (target.isEmpty) return 0;

    final toDelete = _entries.where((entry) {
      final isManual = entry.category == _manualCategory &&
          (entry.project == _manualProjectMarker || entry.question.contains('매뉴얼 섹션'));
      return isManual && (entry.customer ?? '').trim() == target;
    }).toList();

    for (final entry in toDelete) {
      await _repository.deleteEntry(entry.id);
    }

    _entries.removeWhere((entry) => toDelete.any((item) => item.id == entry.id));
    notifyListeners();
    return toDelete.length;
  }

  void setFilter(String category) {
    _filterCategory = category;
    notifyListeners();
  }

  void setSearch(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  List<String> get categories {
    final cats = _entries.map((e) => e.category).toSet().toList();
    cats.sort();
    return cats;
  }

  Map<String, int> get categoryStats {
    final map = <String, int>{};
    for (final e in _entries) {
      map[e.category] = (map[e.category] ?? 0) + 1;
    }
    return map;
  }
}
