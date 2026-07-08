import 'package:flutter/foundation.dart';
import '../../core/constants/app_constants.dart';
import '../../data/services/ai_service.dart';
import '../../domain/entities/knowledge_base_entity.dart';
import '../../domain/repositories/knowledge_base_repository.dart';
import '../../data/services/manual_document_import_service.dart';
import 'settings_viewmodel.dart';

class KnowledgeBaseViewModel extends ChangeNotifier {
  final KnowledgeBaseRepository _repository;
  final SettingsViewModel _settingsViewModel;
  late final ManualDocumentImportService _manualImportService;
  final AiService _aiService = AiService();

  List<KnowledgeBaseEntity> _entries = [];
  bool _isLoading = false;
  bool _isImportingManual = false;
  String? _error;
  String _filterCategory = '';
  String _searchQuery = '';
  static const String _manualCategory = ManualDocumentImportService.manualCategory;
  static const String _manualProjectMarker = 'manual-upload';

  KnowledgeBaseViewModel(this._repository, this._settingsViewModel) {
    _manualImportService = ManualDocumentImportService(_repository);
    _configureAiService();
    _settingsViewModel.addListener(_configureAiService);
    loadEntries();
  }

  @override
  void dispose() {
    _settingsViewModel.removeListener(_configureAiService);
    super.dispose();
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

    if (!_ensureManualImportAiReady()) {
      notifyListeners();
      return null;
    }

    try {
      await _aiService.testConnection();
    } catch (e) {
      _error = 'AI 연결 확인 실패: $e';
      notifyListeners();
      return null;
    }

    _isImportingManual = true;
    _error = null;
    notifyListeners();

    try {
      final result = await _manualImportService.importDocuments(
        normalized,
        answerRefiner: (question, sourceText) => _aiService.refineManualAnswer(
          question: question,
          sourceText: sourceText,
        ),
      );
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

  void _configureAiService() {
    final provider = _settingsViewModel.aiProvider;
    _aiService.setProvider(provider);

    if (provider == AppConstants.aiProviderOllama) {
      _aiService.configureOllama(
        _settingsViewModel.ollamaUrl,
        _settingsViewModel.ollamaModel,
        temperature: _settingsViewModel.aiTemperature,
        maxTokens: _settingsViewModel.aiMaxTokens,
      );
      return;
    }

    if (provider == AppConstants.aiProviderGemini) {
      _aiService.configureGemini(
        _settingsViewModel.geminiKey,
        _settingsViewModel.geminiModel,
        temperature: _settingsViewModel.aiTemperature,
        maxTokens: _settingsViewModel.aiMaxTokens,
      );
      return;
    }

    if (provider == AppConstants.aiProviderClaude) {
      _aiService.configureClaude(
        _settingsViewModel.claudeKey,
        _settingsViewModel.claudeBaseUrl,
        _settingsViewModel.claudeModel,
        temperature: _settingsViewModel.aiTemperature,
        maxTokens: _settingsViewModel.aiMaxTokens,
      );
      return;
    }

    _aiService.configureOpenAi(
      _settingsViewModel.openAiKey,
      _settingsViewModel.openAiModel,
      temperature: _settingsViewModel.aiTemperature,
      maxTokens: _settingsViewModel.aiMaxTokens,
    );
  }

  bool _ensureManualImportAiReady() {
    if (_aiService.isConfigured) {
      return true;
    }
    _error = 'AI 설정이 필요합니다. 설정에서 AI 제공자/API를 먼저 구성해 주세요.';
    return false;
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
