import 'dart:convert';

import '../../domain/entities/knowledge_base_entity.dart';
import '../datasources/remote/gemini_service.dart';
import '../datasources/remote/ollama_service.dart';
import '../datasources/remote/openai_service.dart';
import '../../core/constants/app_constants.dart';

class AiPrompts {
  AiPrompts._();

  static const String vocAnalysisSystem = '''
당신은 IT 시스템 고객 지원 전문가입니다.
사용자의 문의가 업무(IT 시스템, 소프트웨어, 서비스) 관련 VOC인지 판단하는 역할을 합니다.

판단 기준:
- 업무 관련: 시스템 장애, 기능 문의, 사용법, 개선 요청, 운영 문의, 계약 관련 등
- 업무 무관: 일상 대화, 개인적 질문, 시스템과 관계없는 내용

응답 형식 (JSON만 반환, 다른 텍스트 없음):
{
  "is_business": true/false,
  "category": "장애|기능문의|사용법|개선요청|운영문의|계약문의",
  "reason": "판단 이유 한 줄"
}
''';

  static String vocAnalysisUser(String title, String content) =>
      '제목: $title\n내용: $content';

  static const String answerGenerationSystem = '''
당신은 IT 시스템 고객 지원 전문가입니다.
아래 제공된 유사 VOC 사례와 기존 답변만을 참고하여 고객 문의에 대한 답변을 작성하세요.

규칙:
1. 제공된 사례에 없는 내용은 절대 만들어내지 마세요
2. 최대한 기존 답변을 기반으로 작성하세요
3. 답변은 공손하고 전문적인 어조를 유지하세요
4. 없는 사실을 추측하지 마세요
5. 참고한 사례 번호를 반드시 명시하세요

응답 형식 (JSON만 반환):
{
  "answer": "생성된 답변 내용",
  "confidence": 0.0~1.0,
  "referenced_cases": ["사례1 제목", "사례2 제목"],
  "notes": "추가 참고 사항 또는 불확실한 부분"
}
''';

  static String answerGenerationUser(
    String vocTitle,
    String vocContent,
    List<SimilarVocResult> similarCases,
  ) {
    final casesText = similarCases.asMap().entries.map((e) {
      final idx = e.key + 1;
      final kb = e.value.knowledgeBase;
      final score = (e.value.similarityScore * 100).toStringAsFixed(1);
      return '''
[사례 $idx] (유사도: $score%)
질문: ${kb.question}
답변: ${kb.answer}
카테고리: ${kb.category}
''';
    }).join('\n---\n');

    return '''
[고객 문의]
제목: $vocTitle
내용: $vocContent

[유사 사례]
$casesText
''';
  }
}

class AiAnswerResult {
  final String answer;
  final double confidence;
  final List<String> referencedCases;
  final String notes;

  const AiAnswerResult({
    required this.answer,
    required this.confidence,
    required this.referencedCases,
    required this.notes,
  });
}

class VocAnalysisResult {
  final bool isBusiness;
  final String category;
  final String reason;

  const VocAnalysisResult({
    required this.isBusiness,
    required this.category,
    required this.reason,
  });
}

class VocIntelligenceResult {
  final bool isBusiness;
  final double businessScore;
  final String category;
  final double categoryScore;
  final String urgency;
  final double urgencyScore;
  final String department;
  final double departmentScore;
  final String assignee;
  final double assigneeScore;
  final String? duplicateOfVocId;
  final double duplicateScore;
  final bool jiraRequired;
  final double jiraScore;
  final String reason;

  const VocIntelligenceResult({
    required this.isBusiness,
    required this.businessScore,
    required this.category,
    required this.categoryScore,
    required this.urgency,
    required this.urgencyScore,
    required this.department,
    required this.departmentScore,
    required this.assignee,
    required this.assigneeScore,
    this.duplicateOfVocId,
    required this.duplicateScore,
    required this.jiraRequired,
    required this.jiraScore,
    required this.reason,
  });
}

class AssigneeRecommendation {
  final String assignee;
  final double accuracy;
  final int handled;

  const AssigneeRecommendation({
    required this.assignee,
    required this.accuracy,
    required this.handled,
  });
}

class AiService {
  OllamaService? _ollamaService;
  OpenAiService? _openAiService;
  GeminiService? _geminiService;
  String _provider = AppConstants.aiProviderOllama;

  void configureOllama(String baseUrl, String model) {
    _ollamaService = OllamaService(baseUrl: baseUrl, model: model);
    _provider = AppConstants.aiProviderOllama;
  }

  void configureOpenAi(String apiKey, String model) {
    _openAiService = OpenAiService(apiKey: apiKey, chatModel: model);
    _provider = AppConstants.aiProviderOpenAi;
  }

  void configureGemini(String apiKey, String model) {
    _geminiService = GeminiService(apiKey: apiKey, chatModel: model);
    _provider = AppConstants.aiProviderGemini;
  }

  void setProvider(String provider) => _provider = provider;

  bool get isConfigured {
    if (_provider == AppConstants.aiProviderOllama) {
      return _ollamaService != null;
    }
    if (_provider == AppConstants.aiProviderGemini) {
      return _geminiService != null && _geminiService!.apiKey.isNotEmpty;
    }
    return _openAiService != null && _openAiService!.apiKey.isNotEmpty;
  }

  Future<String> _generate(String systemPrompt, String userPrompt) async {
    if (_provider == AppConstants.aiProviderGemini && _geminiService != null) {
      return await _geminiService!.generate(systemPrompt, userPrompt);
    }
    if (_provider == AppConstants.aiProviderOpenAi && _openAiService != null) {
      return await _openAiService!.generate(systemPrompt, userPrompt);
    }
    if (_ollamaService != null) {
      return await _ollamaService!.generate(systemPrompt, userPrompt);
    }
    throw Exception('AI 서비스가 설정되지 않았습니다. 설정에서 AI 제공자를 구성해 주세요.');
  }

  /// VOC 업무 관련 여부 분석
  Future<VocAnalysisResult> analyzeVoc(String title, String content) async {
    final userPrompt = AiPrompts.vocAnalysisUser(title, content);
    final raw = await _generate(AiPrompts.vocAnalysisSystem, userPrompt);

    return _parseVocAnalysis(raw);
  }

  Future<VocIntelligenceResult> analyzeVocIntelligence({
    required String title,
    required String content,
    required List<Map<String, dynamic>> assigneeCandidates,
    required List<Map<String, dynamic>> duplicateCandidates,
  }) async {
    final systemPrompt = '''
당신은 기업 VOC 운영 분석 엔진입니다.
아래 VOC에 대해 다음 항목을 반드시 점수와 함께 판단하세요.
1) 업무 관련 여부
2) 카테고리 분류
3) 긴급도(Critical/High/Medium/Low)
4) 담당 부서 추천
5) 담당자 추천
6) 중복 VOC 여부
7) JIRA 생성 필요 여부

응답은 JSON만 반환:
{
  "is_business": true,
  "business_score": 0.0,
  "category": "장애",
  "category_score": 0.0,
  "urgency": "High",
  "urgency_score": 0.0,
  "department": "플랫폼운영팀",
  "department_score": 0.0,
  "assignee": "홍길동",
  "assignee_score": 0.0,
  "duplicate_of_voc_id": "optional",
  "duplicate_score": 0.0,
  "jira_required": true,
  "jira_score": 0.0,
  "reason": "근거 설명"
}
''';

    final userPrompt = '''
[VOC]
제목: $title
내용: $content

[담당자 후보]
${jsonEncode(assigneeCandidates)}

[중복 후보]
${jsonEncode(duplicateCandidates)}
''';

    final raw = await _generate(systemPrompt, userPrompt);
    try {
      final map = _jsonDecode(_extractJson(raw));
      return VocIntelligenceResult(
        isBusiness: map['is_business'] as bool? ?? true,
        businessScore: (map['business_score'] as num?)?.toDouble() ?? 0.6,
        category: map['category'] as String? ?? '기능문의',
        categoryScore: (map['category_score'] as num?)?.toDouble() ?? 0.6,
        urgency: map['urgency'] as String? ?? 'Medium',
        urgencyScore: (map['urgency_score'] as num?)?.toDouble() ?? 0.5,
        department: map['department'] as String? ?? '고객지원팀',
        departmentScore: (map['department_score'] as num?)?.toDouble() ?? 0.5,
        assignee: map['assignee'] as String? ?? '미지정',
        assigneeScore: (map['assignee_score'] as num?)?.toDouble() ?? 0.5,
        duplicateOfVocId: map['duplicate_of_voc_id'] as String?,
        duplicateScore: (map['duplicate_score'] as num?)?.toDouble() ?? 0.0,
        jiraRequired: map['jira_required'] as bool? ?? false,
        jiraScore: (map['jira_score'] as num?)?.toDouble() ?? 0.5,
        reason: map['reason'] as String? ?? '',
      );
    } catch (_) {
      return VocIntelligenceResult(
        isBusiness: true,
        businessScore: 0.55,
        category: '기능문의',
        categoryScore: 0.5,
        urgency: 'Medium',
        urgencyScore: 0.5,
        department: '고객지원팀',
        departmentScore: 0.4,
        assignee: assigneeCandidates.isNotEmpty
            ? (assigneeCandidates.first['assignee'] as String? ?? '미지정')
            : '미지정',
        assigneeScore: 0.4,
        duplicateOfVocId: duplicateCandidates.isNotEmpty
            ? duplicateCandidates.first['id'] as String?
            : null,
        duplicateScore: duplicateCandidates.isNotEmpty
            ? ((duplicateCandidates.first['score'] as num?)?.toDouble() ?? 0.0)
            : 0.0,
        jiraRequired: false,
        jiraScore: 0.4,
        reason: raw.length > 200 ? raw.substring(0, 200) : raw,
      );
    }
  }

  Future<String> predictUrgencyReason({
    required String title,
    required String content,
    required String urgency,
  }) async {
    final systemPrompt = '''
당신은 VOC 긴급도 평가자입니다. 긴급도 등급의 근거를 2문장 이내로 설명하세요.
''';
    final userPrompt = '제목: $title\n내용: $content\n긴급도: $urgency';
    final raw = await _generate(systemPrompt, userPrompt);
    return raw.trim();
  }

  /// RAG 기반 답변 생성
  Future<AiAnswerResult> generateAnswer(
    String vocTitle,
    String vocContent,
    List<SimilarVocResult> similarCases,
  ) async {
    if (similarCases.isEmpty) {
      return const AiAnswerResult(
        answer: '유사한 사례를 찾지 못했습니다. 담당자가 직접 답변을 작성해 주세요.',
        confidence: 0.0,
        referencedCases: [],
        notes: '지식베이스에 유사 사례 없음',
      );
    }

    final userPrompt = AiPrompts.answerGenerationUser(vocTitle, vocContent, similarCases);
    final raw = await _generate(AiPrompts.answerGenerationSystem, userPrompt);

    return _parseAnswerResult(raw, similarCases);
  }

  VocAnalysisResult _parseVocAnalysis(String raw) {
    try {
      final jsonStr = _extractJson(raw);
      final map = _jsonDecode(jsonStr);
      return VocAnalysisResult(
        isBusiness: map['is_business'] as bool? ?? true,
        category: map['category'] as String? ?? '기능문의',
        reason: map['reason'] as String? ?? '',
      );
    } catch (_) {
      // JSON 파싱 실패 시 텍스트 기반 폴백
      final isReject = raw.toUpperCase().contains('REJECT') ||
          raw.contains('업무와 관련 없') ||
          raw.contains('업무 무관');
      return VocAnalysisResult(
        isBusiness: !isReject,
        category: '기능문의',
        reason: raw.length > 100 ? raw.substring(0, 100) : raw,
      );
    }
  }

  AiAnswerResult _parseAnswerResult(String raw, List<SimilarVocResult> cases) {
    try {
      final jsonStr = _extractJson(raw);
      final map = _jsonDecode(jsonStr);
      return AiAnswerResult(
        answer: map['answer'] as String? ?? raw,
        confidence: (map['confidence'] as num?)?.toDouble() ?? 0.5,
        referencedCases: List<String>.from(map['referenced_cases'] ?? []),
        notes: map['notes'] as String? ?? '',
      );
    } catch (_) {
      return AiAnswerResult(
        answer: raw,
        confidence: 0.5,
        referencedCases: cases.map((c) => c.knowledgeBase.question).toList(),
        notes: '',
      );
    }
  }

  String _extractJson(String text) {
    final start = text.indexOf('{');
    final end = text.lastIndexOf('}');
    if (start == -1 || end == -1) return text;
    return text.substring(start, end + 1);
  }

  Map<String, dynamic> _jsonDecode(String json) {
    return Map<String, dynamic>.from(jsonDecode(json) as Map);
  }
}
