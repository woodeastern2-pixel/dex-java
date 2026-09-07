import 'dart:convert';
import 'package:http/http.dart' as http;

class OpenAiService {
  final String apiKey;
  String chatModel;
  final double temperature;
  final int maxTokens;
  static const String _embeddingModel = 'text-embedding-3-small';
  static const String _baseUrl = 'https://api.openai.com/v1';

  OpenAiService({
    required this.apiKey,
    required this.chatModel,
    this.temperature = 0.3,
    this.maxTokens = 2048,
  });

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
      };

  /// 채팅 완성
  Future<String> generate(String systemPrompt, String userPrompt) async {
    final url = Uri.parse('$_baseUrl/chat/completions');
    final body = jsonEncode({
      'model': chatModel,
      'messages': [
        {'role': 'system', 'content': systemPrompt},
        {'role': 'user', 'content': userPrompt},
      ],
      'temperature': temperature,
      'max_tokens': maxTokens,
    });

    final response = await http
        .post(url, headers: _headers, body: body)
        .timeout(const Duration(seconds: 120));

    if (response.statusCode != 200) {
      throw Exception('OpenAI 오류 (${response.statusCode}): ${response.body}');
    }

    final data = jsonDecode(utf8.decode(response.bodyBytes));
    return data['choices'][0]['message']['content'] as String;
  }

  /// 텍스트 임베딩 생성
  Future<List<double>> embed(String text) async {
    final url = Uri.parse('$_baseUrl/embeddings');
    final body = jsonEncode({
      'model': _embeddingModel,
      'input': text,
    });

    final response = await http
        .post(url, headers: _headers, body: body)
        .timeout(const Duration(seconds: 30));

    if (response.statusCode != 200) {
      throw Exception('OpenAI 임베딩 오류: ${response.body}');
    }

    final data = jsonDecode(response.body);
    return List<double>.from(data['data'][0]['embedding']);
  }

  /// API Key 유효성 검증
  Future<bool> isValidApiKey() async {
    try {
      final url = Uri.parse('$_baseUrl/models');
      final response = await http
          .get(url, headers: _headers)
          .timeout(const Duration(seconds: 10));
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }
}
