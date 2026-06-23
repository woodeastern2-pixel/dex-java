import 'dart:convert';
import 'package:http/http.dart' as http;

class GeminiService {
  final String apiKey;
  String chatModel;

  static const String _embeddingModel = 'text-embedding-004';
  static const String _baseUrl = 'https://generativelanguage.googleapis.com/v1beta';

  GeminiService({required this.apiKey, required this.chatModel});

  /// 텍스트 생성
  Future<String> generate(String systemPrompt, String userPrompt) async {
    final url = Uri.parse(
      '$_baseUrl/models/$chatModel:generateContent?key=$apiKey',
    );

    final body = jsonEncode({
      'systemInstruction': {
        'parts': [
          {'text': systemPrompt}
        ]
      },
      'contents': [
        {
          'parts': [
            {'text': userPrompt}
          ]
        }
      ],
      'generationConfig': {
        'temperature': 0.3,
        'maxOutputTokens': 2048,
      }
    });

    final response = await http
        .post(
          url,
          headers: {'Content-Type': 'application/json'},
          body: body,
        )
        .timeout(const Duration(seconds: 120));

    if (response.statusCode != 200) {
      throw Exception('Gemini 오류 (${response.statusCode}): ${response.body}');
    }

    final data = jsonDecode(utf8.decode(response.bodyBytes));
    final candidates = data['candidates'] as List? ?? [];
    if (candidates.isEmpty) {
      throw Exception('Gemini 응답이 비어 있습니다.');
    }

    final content = candidates.first['content'] as Map<String, dynamic>?;
    final parts = content?['parts'] as List? ?? [];
    if (parts.isEmpty) {
      throw Exception('Gemini 응답 텍스트를 찾을 수 없습니다.');
    }

    final text = parts
        .map((p) => (p as Map<String, dynamic>)['text'] as String? ?? '')
        .join()
        .trim();

    if (text.isEmpty) {
      throw Exception('Gemini 응답 텍스트가 비어 있습니다.');
    }

    return text;
  }

  /// 텍스트 임베딩 생성
  Future<List<double>> embed(String text) async {
    final url = Uri.parse(
      '$_baseUrl/models/$_embeddingModel:embedContent?key=$apiKey',
    );
    final body = jsonEncode({
      'content': {
        'parts': [
          {'text': text}
        ]
      }
    });

    final response = await http
        .post(
          url,
          headers: {'Content-Type': 'application/json'},
          body: body,
        )
        .timeout(const Duration(seconds: 30));

    if (response.statusCode != 200) {
      throw Exception('Gemini 임베딩 오류 (${response.statusCode}): ${response.body}');
    }

    final data = jsonDecode(utf8.decode(response.bodyBytes));
    final values = data['embedding']?['values'] as List?;
    if (values == null || values.isEmpty) {
      throw Exception('Gemini 임베딩 값이 비어 있습니다.');
    }

    return List<double>.from(values);
  }

  /// API Key 유효성 검증
  Future<bool> isValidApiKey() async {
    try {
      final url = Uri.parse('$_baseUrl/models?key=$apiKey');
      final response = await http.get(url).timeout(const Duration(seconds: 10));
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }
}
