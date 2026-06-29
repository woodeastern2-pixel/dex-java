import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import 'dart:convert';

import '../../core/constants/app_constants.dart';
import '../../core/database/database_helper.dart';
import '../../core/utils/vector_utils.dart';
import '../../domain/entities/ai_chat_message_entity.dart';
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
  bool _isChatting = false;
  String? _error;
  String? _chatError;

  VocAnalysisResult? _analysisResult;
  VocIntelligenceResult? _intelligenceResult;
  List<SimilarVocResult> _similarVocs = [];
  AiAnswerResult? _answerResult;
  String? _urgencyReason;
  List<AssigneeRecommendation> _topAssignees = [];
  List<AiChatMessageEntity> _chatMessages = [];
  String? _activeChatSessionId;

  AiViewModel(this._kbRepository, this._vocRepository, this._settingsViewModel) {
    _aiService = AiService();
    _vectorSearch = VectorSearchService(_kbRepository);
    _configureServices();
    _settingsViewModel.addListener(_configureServices);
  }

  bool get isAnalyzing => _isAnalyzing;
  bool get isGenerating => _isGenerating;
  bool get isSearching => _isSearching;
  bool get isChatting => _isChatting;
  String? get error => _error;
  String? get chatError => _chatError;
  VocAnalysisResult? get analysisResult => _analysisResult;
  VocIntelligenceResult? get intelligenceResult => _intelligenceResult;
  List<SimilarVocResult> get similarVocs => _similarVocs;
  AiAnswerResult? get answerResult => _answerResult;
  bool get hasAnswer => _answerResult != null;
  String? get urgencyReason => _urgencyReason;
  List<AssigneeRecommendation> get topAssignees => _topAssignees;
  List<AiChatMessageEntity> get chatMessages => _chatMessages;
  String? get activeChatSessionId => _activeChatSessionId;

  void _configureServices() {
    final provider = _settingsViewModel.aiProvider;
    _aiService.setProvider(provider);
    _vectorSearch.setProvider(provider);
    _vectorSearch.configureFaiss(_settingsViewModel.faissEndpoint);

    if (provider == AppConstants.aiProviderOllama) {
      _aiService.configureOllama(
        _settingsViewModel.ollamaUrl,
        _settingsViewModel.ollamaModel,
        temperature: _settingsViewModel.aiTemperature,
        maxTokens: _settingsViewModel.aiMaxTokens,
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
        temperature: _settingsViewModel.aiTemperature,
        maxTokens: _settingsViewModel.aiMaxTokens,
      );
      _vectorSearch.configureGemini(
        _settingsViewModel.geminiKey,
        _settingsViewModel.geminiModel,
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
    _vectorSearch.configureOpenAi(
      _settingsViewModel.openAiKey,
      _settingsViewModel.openAiModel,
    );
  }

  /// 1단계: VOC 업무 관련 여부 분석
  Future<VocAnalysisResult?> analyzeVoc(String title, String content) async {
    if (!_ensureAiConfigured()) {
      return null;
    }
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
    if (!_ensureAiConfigured()) {
      return null;
    }
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
      final reranked = await _aiService.rerankSimilarCases(
        query: query,
        candidates: mergedList.take(20).toList(),
      );
      _similarVocs = reranked.take(AppConstants.topKSimilar).toList();
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
          adoptionCount: selected.adoptionCount,
          usageCount: selected.usageCount,
          lastUsedAt: selected.lastUsedAt,
        ),
      );
    }

    results.sort((a, b) => b.similarityScore.compareTo(a.similarityScore));
    return results.take(AppConstants.topKSimilar).toList();
  }

  /// 3단계: AI 답변 생성 (RAG)
  Future<AiAnswerResult?> generateAnswer(String title, String content) async {
    if (!_ensureAiConfigured()) {
      return null;
    }
    _isGenerating = true;
    _error = null;
    _answerResult = null;
    notifyListeners();

    try {
      // 유사 VOC 검색 (없으면 다시 검색)
      if (_similarVocs.isEmpty) {
        await searchSimilarVocs('$title $content');
      }
      final answerCases = _similarVocs.take(AppConstants.topKSimilar).toList();
      _answerResult = await _aiService.generateAnswer(title, content, answerCases);
      return _answerResult;
    } catch (e) {
      _error = '답변 생성 실패: $e';
      return null;
    } finally {
      _isGenerating = false;
      notifyListeners();
    }
  }

  Future<String> testConnection() async {
    _configureServices();
    if (!_ensureAiConfigured()) {
      throw Exception(_error ?? 'AI 제공자 설정이 완료되지 않았습니다.');
    }

    try {
      final result = await _aiService.testConnection();
      _error = null;
      notifyListeners();
      return result;
    } catch (e) {
      _error = 'AI 통신 테스트 실패: $e';
      notifyListeners();
      rethrow;
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

  Future<void> startChatSession(String sessionId) async {
    _activeChatSessionId = sessionId;
    await loadChatMessages(sessionId);
  }

  Future<List<AiChatMessageEntity>> loadChatMessages(String sessionId) async {
    final db = await DatabaseHelper.instance.database;
    final rows = await db.query(
      'ai_chat_messages',
      where: 'session_id = ?',
      whereArgs: [sessionId],
      orderBy: 'created_at ASC',
    );
    _chatMessages = rows.map(_mapChatMessage).toList();
    _chatError = null;
    notifyListeners();
    return _chatMessages;
  }

  Future<AiChatMessageEntity?> sendChatMessage(String content) async {
    if (!_ensureAiConfigured(forChat: true)) {
      return null;
    }
    final sessionId = _activeChatSessionId;
    if (sessionId == null) {
      _chatError = '채팅 세션이 초기화되지 않았습니다.';
      notifyListeners();
      return null;
    }

    final trimmed = content.trim();
    if (trimmed.isEmpty) return null;

    _isChatting = true;
    _chatError = null;
    notifyListeners();

    try {
      final userMessage = await _insertChatMessage(
        sessionId: sessionId,
        role: 'user',
        content: trimmed,
        category: 'general',
      );
      _chatMessages = [..._chatMessages, userMessage];
      notifyListeners();

      final references = await _getChatReferences(trimmed);
      final reply = await _aiService.generateChatReply(
        message: trimmed,
        history: _chatMessages.take(_chatMessages.length - 1).toList(),
        references: references,
      );

      final assistantMessage = await _insertChatMessage(
        sessionId: sessionId,
        role: 'assistant',
        content: reply,
        category: 'general',
        referencedVocIds: references.map((item) => item.knowledgeBase.vocId).whereType<String>().toList(),
        confidence: references.isEmpty ? null : references.first.similarityScore,
      );
      _chatMessages = [..._chatMessages, assistantMessage];
      notifyListeners();
      return assistantMessage;
    } catch (e) {
      _chatError = '채팅 실패: $e';
      notifyListeners();
      return null;
    } finally {
      _isChatting = false;
      notifyListeners();
    }
  }

  Future<void> clearChatSession() async {
    _activeChatSessionId = null;
    _chatMessages = [];
    _chatError = null;
    notifyListeners();
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

  AiChatMessageEntity _mapChatMessage(Map<String, Object?> row) {
    final rawRefs = row['referenced_voc_ids'] as String?;
    final refs = rawRefs == null || rawRefs.isEmpty
        ? <String>[]
        : List<String>.from(jsonDecode(rawRefs) as List);
    return AiChatMessageEntity(
      id: row['id'] as String,
      sessionId: row['session_id'] as String,
      category: row['category'] as String,
      role: row['role'] as String,
      content: row['content'] as String,
      referencedVocIds: refs,
      confidence: (row['confidence'] as num?)?.toDouble(),
      createdAt: DateTime.parse(row['created_at'] as String),
    );
  }

  Future<AiChatMessageEntity> _insertChatMessage({
    required String sessionId,
    required String role,
    required String content,
    required String category,
    List<String>? referencedVocIds,
    double? confidence,
  }) async {
    final now = DateTime.now();
    final message = AiChatMessageEntity(
      id: _uuid.v4(),
      sessionId: sessionId,
      category: category,
      role: role,
      content: content,
      referencedVocIds: referencedVocIds ?? const [],
      confidence: confidence,
      createdAt: now,
    );
    final db = await DatabaseHelper.instance.database;
    await db.insert('ai_chat_messages', {
      'id': message.id,
      'session_id': message.sessionId,
      'category': message.category,
      'role': message.role,
      'content': message.content,
      'referenced_voc_ids': jsonEncode(message.referencedVocIds),
      'confidence': message.confidence,
      'created_at': message.createdAt.toIso8601String(),
    });
    return message;
  }

  Future<List<SimilarVocResult>> _getChatReferences(String query) async {
    final results = await _vectorSearch.searchSimilar(query, topK: 20);
    final reranked = await _aiService.rerankSimilarCases(query: query, candidates: results);
    return reranked.take(5).toList();
  }

  bool _ensureAiConfigured({bool forChat = false}) {
    if (_aiService.isConfigured) return true;
    final message = 'AI 제공자 설정이 완료되지 않았습니다. 설정 > AI 설정에서 API Key/모델을 확인해 주세요.';
    if (forChat) {
      _chatError = message;
    } else {
      _error = message;
    }
    notifyListeners();
    return false;
  }

  @override
  void dispose() {
    _settingsViewModel.removeListener(_configureServices);
    super.dispose();
  }
}
