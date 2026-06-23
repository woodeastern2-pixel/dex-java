import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../../core/constants/app_constants.dart';
import '../../core/utils/vector_utils.dart';
import '../../domain/entities/knowledge_base_entity.dart';
import '../../domain/repositories/knowledge_base_repository.dart';
import '../../domain/repositories/voc_repository.dart';
import '../../data/services/ai_service.dart';
import '../../data/services/vector_search_service.dart';
import 'settings_viewmodel.dart';

class AiViewModel extends ChangeNotifier {
  final KnowledgeBaseRepository _kbRepository;
  final VocRepository _vocRepository;
  final SettingsViewModel _settingsViewModel;
  final _uuid = const Uuid();

  late final AiService _aiService;
  late final VectorSearchService _vectorSearch;

  bool _isAnalyzing = false;
  bool _isGenerating = false;
  bool _isSearching = false;
  String? _error;

  VocAnalysisResult? _analysisResult;
  VocIntelligenceResult? _intelligenceResult;
  List<SimilarVocResult> _similarVocs = [];
  AiAnswerResult? _answerResult;
  String? _urgencyReason;
  List<AssigneeRecommendation> _topAssignees = [];

  AiViewModel(this._kbRepository, this._vocRepository, this._settingsViewModel) {
    _aiService = AiService();
    _vectorSearch = VectorSearchService(_kbRepository);
    _configureServices();
    _settingsViewModel.addListener(_configureServices);
  }

  bool get isAnalyzing => _isAnalyzing;
  bool get isGenerating => _isGenerating;
  bool get isSearching => _isSearching;
  String? get error => _error;
  VocAnalysisResult? get analysisResult => _analysisResult;
  VocIntelligenceResult? get intelligenceResult => _intelligenceResult;
  List<SimilarVocResult> get similarVocs => _similarVocs;
  AiAnswerResult? get answerResult => _answerResult;
  bool get hasAnswer => _answerResult != null;
  String? get urgencyReason => _urgencyReason;
  List<AssigneeRecommendation> get topAssignees => _topAssignees;

  void _configureServices() {
    final provider = _settingsViewModel.aiProvider;
    _aiService.setProvider(provider);
    _vectorSearch.setProvider(provider);
    _vectorSearch.configureFaiss(_settingsViewModel.faissEndpoint);

    if (provider == AppConstants.aiProviderOllama) {
      _aiService.configureOllama(
        _settingsViewModel.ollamaUrl,
        _settingsViewModel.ollamaModel,
      );
      _vectorSearch.configureOllama(
        _settingsViewModel.ollamaUrl,
        _settingsViewModel.ollamaModel,
      );
      return;
    }

    if (provider == AppConstants.aiProviderGemini) {
      _aiService.configureGemini(
        _settingsViewModel.geminiKey,
        _settingsViewModel.geminiModel,
      );
      _vectorSearch.configureGemini(
        _settingsViewModel.geminiKey,
        _settingsViewModel.geminiModel,
      );
      return;
    }

    _aiService.configureOpenAi(
      _settingsViewModel.openAiKey,
      _settingsViewModel.openAiModel,
    );
    _vectorSearch.configureOpenAi(
      _settingsViewModel.openAiKey,
      _settingsViewModel.openAiModel,
    );
  }

  /// 1단계: VOC 업무 관련 여부 분석
  Future<VocAnalysisResult?> analyzeVoc(String title, String content) async {
    _isAnalyzing = true;
    _error = null;
    _analysisResult = null;
    notifyListeners();

    try {
      _analysisResult = await _aiService.analyzeVoc(title, content);
      return _analysisResult;
    } catch (e) {
      _error = '분석 실패: $e';
      return null;
    } finally {
      _isAnalyzing = false;
      notifyListeners();
    }
  }

  Future<VocIntelligenceResult?> analyzeVocIntelligence(
    String title,
    String content,
  ) async {
    _isAnalyzing = true;
    _error = null;
    _intelligenceResult = null;
    _urgencyReason = null;
    notifyListeners();

    try {
      final assigneeStats = await _vocRepository.getTopAssigneeStats(topN: 10);
      _topAssignees = assigneeStats
          .map(
            (e) => AssigneeRecommendation(
              assignee: e['assignee'] as String,
              accuracy: (e['accuracy'] as num?)?.toDouble() ?? 0.0,
              handled: e['handled'] as int? ?? 0,
            ),
          )
          .take(3)
          .toList();

      final allVocs = await _vocRepository.getAllVocs();
      final queryEmb = VectorUtils.simpleTextEmbedding('$title $content');
      final dupCandidates = allVocs
          .map((v) {
            final emb = v.embedding ?? VectorUtils.simpleTextEmbedding('${v.title} ${v.content}');
            final score = VectorUtils.cosineSimilarity(queryEmb, emb);
            return {
              'id': v.id,
              'title': v.title,
              'content': v.content,
              'score': score,
            };
          })
          .where((m) => ((m['score'] as double?) ?? 0) >= 0.85)
          .toList()
        ..sort((a, b) => ((b['score'] as double?) ?? 0).compareTo((a['score'] as double?) ?? 0));

      _intelligenceResult = await _aiService.analyzeVocIntelligence(
        title: title,
        content: content,
        assigneeCandidates: assigneeStats,
        duplicateCandidates: dupCandidates.take(5).toList(),
      );

      _urgencyReason = await _aiService.predictUrgencyReason(
        title: title,
        content: content,
        urgency: _intelligenceResult!.urgency,
      );

      return _intelligenceResult;
    } catch (e) {
      _error = '고급 분석 실패: $e';
      return null;
    } finally {
      _isAnalyzing = false;
      notifyListeners();
    }
  }

  /// 2단계: 유사 VOC 검색
  Future<List<SimilarVocResult>> searchSimilarVocs(String query) async {
    _isSearching = true;
    _error = null;
    notifyListeners();

    try {
      final kbSimilar = await _vectorSearch.searchSimilar(query);
      final vocResponseSimilar = await _searchSimilarFromVocResponses(query);

      final merged = <String, SimilarVocResult>{};
      for (final item in [...kbSimilar, ...vocResponseSimilar]) {
        final key = item.knowledgeBase.id;
        final prev = merged[key];
        if (prev == null || item.similarityScore > prev.similarityScore) {
          merged[key] = item;
        }
      }

      final mergedList = merged.values.toList()
        ..sort((a, b) => b.similarityScore.compareTo(a.similarityScore));
      _similarVocs = mergedList.take(AppConstants.topKSimilar).toList();
      return _similarVocs;
    } catch (e) {
      _error = '유사 VOC 검색 실패: $e';
      _similarVocs = [];
      return [];
    } finally {
      _isSearching = false;
      notifyListeners();
    }
  }

  Future<List<SimilarVocResult>> _searchSimilarFromVocResponses(
    String query,
  ) async {
    final queryEmb = VectorUtils.simpleTextEmbedding(query);
    final vocs = await _vocRepository.getAllVocs();
    final results = <SimilarVocResult>[];

    for (final voc in vocs) {
      final responses = await _vocRepository.getResponsesByVocId(voc.id);
      if (responses.isEmpty) {
        continue;
      }

      responses.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      final selected = responses.firstWhere(
        (r) => r.status == AppConstants.responseApproved,
        orElse: () => responses.first,
      );

      final vocEmb =
          voc.embedding ?? VectorUtils.simpleTextEmbedding('${voc.title} ${voc.content}');
      final answerEmb = VectorUtils.simpleTextEmbedding(selected.content);

      final vocScore = VectorUtils.cosineSimilarity(queryEmb, vocEmb);
      final answerScore = VectorUtils.cosineSimilarity(queryEmb, answerEmb);
      final similarity = (vocScore * 0.6 + answerScore * 0.4).clamp(0.0, 1.0);

      if (similarity < AppConstants.similarityThreshold) {
        continue;
      }

      results.add(
        SimilarVocResult(
          knowledgeBase: KnowledgeBaseEntity(
            id: 'voc-case-${voc.id}-${selected.id}',
            question: voc.title,
            answer: selected.content,
            category: voc.category,
            customer: voc.customer,
            project: voc.project,
            vocId: voc.id,
            resolvedAt: selected.updatedAt,
            createdAt: selected.createdAt,
          ),
          similarityScore: similarity,
        ),
      );
    }

    results.sort((a, b) => b.similarityScore.compareTo(a.similarityScore));
    return results.take(AppConstants.topKSimilar).toList();
  }

  /// 3단계: AI 답변 생성 (RAG)
  Future<AiAnswerResult?> generateAnswer(String title, String content) async {
    _isGenerating = true;
    _error = null;
    _answerResult = null;
    notifyListeners();

    try {
      // 유사 VOC 검색 (없으면 다시 검색)
      if (_similarVocs.isEmpty) {
        _similarVocs = await _vectorSearch.searchSimilar('$title $content');
      }
      _answerResult = await _aiService.generateAnswer(title, content, _similarVocs);
      return _answerResult;
    } catch (e) {
      _error = '답변 생성 실패: $e';
      return null;
    } finally {
      _isGenerating = false;
      notifyListeners();
    }
  }

  /// 지식베이스에 VOC+답변 저장
  Future<KnowledgeBaseEntity> saveToKnowledgeBase({
    required String question,
    required String answer,
    required String category,
    String? customer,
    String? project,
    String? vocId,
  }) async {
    final now = DateTime.now();
    final entry = KnowledgeBaseEntity(
      id: _uuid.v4(),
      question: question,
      answer: answer,
      category: category,
      customer: customer,
      project: project,
      vocId: vocId,
      resolvedAt: now,
      createdAt: now,
    );
    final saved = await _kbRepository.createEntry(entry);
    // 백그라운드로 임베딩 인덱싱
    _vectorSearch.indexEntry(saved);
    return saved;
  }

  void clearResults() {
    _analysisResult = null;
    _intelligenceResult = null;
    _similarVocs = [];
    _answerResult = null;
    _urgencyReason = null;
    _topAssignees = [];
    _error = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _settingsViewModel.removeListener(_configureServices);
    super.dispose();
  }
}
