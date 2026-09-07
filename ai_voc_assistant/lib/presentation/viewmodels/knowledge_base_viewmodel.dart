import 'package:flutter/foundation.dart';
import '../../domain/entities/knowledge_base_entity.dart';
import '../../domain/repositories/knowledge_base_repository.dart';

class KnowledgeBaseViewModel extends ChangeNotifier {
  final KnowledgeBaseRepository _repository;

  List<KnowledgeBaseEntity> _entries = [];
  bool _isLoading = false;
  String? _error;
  String _filterCategory = '';
  String _searchQuery = '';

  KnowledgeBaseViewModel(this._repository) {
    loadEntries();
  }

  List<KnowledgeBaseEntity> get entries => _filtered;
  bool get isLoading => _isLoading;
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
