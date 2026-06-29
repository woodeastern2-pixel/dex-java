import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

class GeminiService {
  final String apiKey;
  String chatModel;
  final double temperature;
  final int maxTokens;

  static const String _embeddingModel = 'text-embedding-004';
  static const String _baseUrl = 'https://generativelanguage.googleapis.com/v1beta';
  static const String _fallbackBaseUrl = 'https://www.googleapis.com/generativeLanguage/v1beta';

  GeminiService({
    required this.apiKey,
    required this.chatModel,
    this.temperature = 0.3,
    this.maxTokens = 2048,
  });

  /// 텍스트 생성
  Future<String> generate(String systemPrompt, String userPrompt) async {
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
        'temperature': temperature,
        'maxOutputTokens': maxTokens,
      }
    });

    final response = await _postWithFallback(
      path: '/models/$chatModel:generateContent',
      body: body,
      timeout: const Duration(seconds: 120),
      timeoutMessage: 'Gemini 요청 시간 초과입니다. 네트워크 상태를 확인하거나 잠시 후 다시 시도해 주세요.',
    );

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
    final body = jsonEncode({
      'content': {
        'parts': [
          {'text': text}
        ]
      }
    });

    final response = await _postWithFallback(
      path: '/models/$_embeddingModel:embedContent',
      body: body,
      timeout: const Duration(seconds: 30),
      timeoutMessage: 'Gemini 임베딩 요청 시간 초과입니다. 네트워크 상태를 확인하거나 잠시 후 다시 시도해 주세요.',
    );

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
      final primary = Uri.parse('$_baseUrl/models?key=$apiKey');
      final response = await http.get(primary).timeout(const Duration(seconds: 10));
      if (response.statusCode == 200) return true;

      final fallback = Uri.parse('$_fallbackBaseUrl/models?key=$apiKey');
      final fallbackResponse = await http.get(fallback).timeout(const Duration(seconds: 10));
      return fallbackResponse.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<http.Response> _postWithFallback({
    required String path,
    required String body,
    required Duration timeout,
    required String timeoutMessage,
  }) async {
    final primary = Uri.parse('$_baseUrl$path?key=$apiKey');
    try {
      return await http
          .post(
            primary,
            headers: {'Content-Type': 'application/json'},
            body: body,
          )
          .timeout(timeout);
    } on SocketException catch (_) {
      final fallback = Uri.parse('$_fallbackBaseUrl$path?key=$apiKey');
      try {
        return await http
            .post(
              fallback,
              headers: {'Content-Type': 'application/json'},
              body: body,
            )
            .timeout(timeout);
      } on SocketException catch (e) {
        throw Exception(_toNetworkMessage(e.toString()));
      } on http.ClientException catch (e) {
        throw Exception(_toNetworkMessage(e.toString()));
      } on TimeoutException {
        throw Exception(timeoutMessage);
      }
    } on http.ClientException catch (_) {
      final fallback = Uri.parse('$_fallbackBaseUrl$path?key=$apiKey');
      try {
        return await http
            .post(
              fallback,
              headers: {'Content-Type': 'application/json'},
              body: body,
            )
            .timeout(timeout);
      } on SocketException catch (e) {
        throw Exception(_toNetworkMessage(e.toString()));
      } on http.ClientException catch (e) {
        throw Exception(_toNetworkMessage(e.toString()));
      } on TimeoutException {
        throw Exception(timeoutMessage);
      }
    } on TimeoutException {
      throw Exception(timeoutMessage);
    }
  }

  String _toNetworkMessage(String raw) {
    final lower = raw.toLowerCase();
    if (lower.contains('failed host lookup') || lower.contains('no address associated with hostname')) {
      return 'Gemini 도메인 DNS 조회에 실패했습니다. 네트워크/VPN/방화벽/사설 DNS 설정을 확인해 주세요.\n'
          '확인 대상: generativelanguage.googleapis.com';
    }
    return 'Gemini 네트워크 연결에 실패했습니다: $raw';
  }
}
