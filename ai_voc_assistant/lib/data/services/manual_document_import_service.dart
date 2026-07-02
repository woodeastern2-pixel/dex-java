import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:excel/excel.dart';
import 'package:path/path.dart' as p;
import 'package:syncfusion_flutter_pdf/pdf.dart';
import 'package:xml/xml.dart';

import '../../core/utils/vector_utils.dart';
import '../../domain/entities/knowledge_base_entity.dart';
import '../../domain/repositories/knowledge_base_repository.dart';

class ManualImportResult {
  final int selectedFiles;
  final int processedFiles;
  final int importedEntries;
  final int updatedEntries;
  final List<String> warnings;

  const ManualImportResult({
    required this.selectedFiles,
    required this.processedFiles,
    required this.importedEntries,
    required this.updatedEntries,
    required this.warnings,
  });
}

class ManualDocumentImportService {
  ManualDocumentImportService(this._kbRepository);

  static const String manualCategory = '시스템매뉴얼';

  final KnowledgeBaseRepository _kbRepository;

  Future<ManualImportResult> importDocuments(List<String> filePaths) async {
    int processedFiles = 0;
    int importedEntries = 0;
    int updatedEntries = 0;
    final warnings = <String>[];

    for (final filePath in filePaths) {
      try {
        final file = File(filePath);
        if (!await file.exists()) {
          warnings.add('파일을 찾을 수 없음: $filePath');
          continue;
        }

        final extension = p.extension(file.path).toLowerCase().replaceAll('.', '');
        final fileName = p.basename(file.path);
        final text = await _extractText(file.path, extension);
        final normalized = _normalizeText(text);

        if (normalized.trim().isEmpty) {
          warnings.add('$fileName: 텍스트 추출 결과가 비어 있어 건너뜀');
          continue;
        }

        final chunks = _chunkText(normalized);
        if (chunks.isEmpty) {
          warnings.add('$fileName: 처리 가능한 본문이 없어 건너뜀');
          continue;
        }

        for (int i = 0; i < chunks.length; i++) {
          final chunk = chunks[i];
          final id = _buildDeterministicId(file.path, i, chunk);
          final question = '[$fileName] 매뉴얼 섹션 ${i + 1}: ${_headline(chunk)}';
          final now = DateTime.now();
          final embedding = VectorUtils.simpleTextEmbedding('$question $chunk');

          final entity = KnowledgeBaseEntity(
            id: id,
            question: question,
            answer: chunk,
            category: manualCategory,
            customer: fileName,
            project: 'manual-upload',
            embedding: embedding,
            resolvedAt: now,
            createdAt: now,
          );

          final existing = await _kbRepository.getEntryById(id);
          if (existing == null) {
            await _kbRepository.createEntry(entity);
            importedEntries++;
          } else {
            await _kbRepository.updateEntry(entity);
            updatedEntries++;
          }
        }

        processedFiles++;
      } catch (e) {
        warnings.add('${p.basename(filePath)}: $e');
      }
    }

    return ManualImportResult(
      selectedFiles: filePaths.length,
      processedFiles: processedFiles,
      importedEntries: importedEntries,
      updatedEntries: updatedEntries,
      warnings: warnings,
    );
  }

  bool isSupported(String fileName) {
    final ext = p.extension(fileName).toLowerCase().replaceAll('.', '');
    return const ['pdf', 'docx', 'xlsx', 'pptx', 'doc', 'xls', 'ppt'].contains(ext);
  }

  Future<String> _extractText(String filePath, String extension) async {
    if (extension == 'pdf') {
      return _extractFromPdf(filePath);
    }
    if (extension == 'docx') {
      return _extractFromDocx(filePath);
    }
    if (extension == 'xlsx') {
      return _extractFromXlsx(filePath);
    }
    if (extension == 'pptx') {
      return _extractFromPptx(filePath);
    }

    if (extension == 'doc' || extension == 'xls' || extension == 'ppt') {
      throw Exception('구형 포맷($extension)은 직접 파싱이 어렵습니다. OpenXML(docx/xlsx/pptx)로 저장 후 업로드해 주세요.');
    }

    throw Exception('지원하지 않는 확장자: $extension');
  }

  Future<String> _extractFromPdf(String filePath) async {
    final bytes = await File(filePath).readAsBytes();
    final document = PdfDocument(inputBytes: bytes);
    try {
      final extractor = PdfTextExtractor(document);
      final text = extractor.extractText();
      return text;
    } finally {
      document.dispose();
    }
  }

  Future<String> _extractFromDocx(String filePath) async {
    final archive = await _readZipArchive(filePath);
    final parts = <String>[];

    for (final entry in archive.files) {
      if (!entry.isFile) continue;
      if (!entry.name.startsWith('word/')) continue;
      if (!entry.name.endsWith('.xml')) continue;

      final content = utf8.decode(entry.content as List<int>, allowMalformed: true);
      parts.add(_extractTextNodesFromXml(content));
    }

    return parts.join('\n\n');
  }

  Future<String> _extractFromPptx(String filePath) async {
    final archive = await _readZipArchive(filePath);
    final slideEntries = archive.files
        .where((entry) => entry.isFile && entry.name.startsWith('ppt/slides/slide') && entry.name.endsWith('.xml'))
        .toList()
      ..sort((a, b) => a.name.compareTo(b.name));

    final parts = <String>[];
    for (final entry in slideEntries) {
      final content = utf8.decode(entry.content as List<int>, allowMalformed: true);
      parts.add(_extractTextNodesFromXml(content));
    }

    return parts.join('\n\n');
  }

  Future<String> _extractFromXlsx(String filePath) async {
    final bytes = await File(filePath).readAsBytes();
    final excel = Excel.decodeBytes(bytes);
    final buffer = StringBuffer();

    for (final tableEntry in excel.tables.entries) {
      final sheetName = tableEntry.key;
      final sheet = tableEntry.value;
      if (sheet == null) continue;

      buffer.writeln('[시트] $sheetName');
      for (final row in sheet.rows) {
        final values = row
            .map((cell) => cell?.value?.toString().trim() ?? '')
            .where((value) => value.isNotEmpty)
            .toList();
        if (values.isEmpty) continue;
        buffer.writeln(values.join(' | '));
      }
      buffer.writeln();
    }

    return buffer.toString();
  }

  Future<Archive> _readZipArchive(String filePath) async {
    final bytes = await File(filePath).readAsBytes();
    final archive = ZipDecoder().decodeBytes(bytes, verify: true);
    return archive;
  }

  String _extractTextNodesFromXml(String xmlContent) {
    final document = XmlDocument.parse(xmlContent);
    final texts = document
        .descendants
        .whereType<XmlElement>()
        .where((e) => e.name.local == 't')
        .map((e) => e.innerText)
        .where((text) => text.trim().isNotEmpty)
        .toList();
    return texts.join(' ');
  }

  String _normalizeText(String input) {
    return input
        .replaceAll('\r', '\n')
        .replaceAll(RegExp(r'\n{3,}'), '\n\n')
        .replaceAll(RegExp(r'[ \t]+'), ' ')
        .trim();
  }

  List<String> _chunkText(String input) {
    const maxChars = 900;
    final paragraphs = input
        .split(RegExp(r'\n\s*\n'))
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();

    final chunks = <String>[];
    var current = StringBuffer();

    for (final paragraph in paragraphs) {
      final nextLength = current.length == 0
          ? paragraph.length
          : current.length + 2 + paragraph.length;
      if (nextLength > maxChars && current.isNotEmpty) {
        chunks.add(current.toString().trim());
        current = StringBuffer();
      }

      if (paragraph.length > maxChars) {
        final split = _hardSplit(paragraph, maxChars);
        for (int i = 0; i < split.length; i++) {
          final piece = split[i];
          if (piece.trim().isEmpty) continue;
          if (i == split.length - 1 && current.isNotEmpty && current.length + piece.length + 2 <= maxChars) {
            current.write('\n\n');
            current.write(piece);
          } else {
            chunks.add(piece.trim());
          }
        }
        continue;
      }

      if (current.isNotEmpty) {
        current.write('\n\n');
      }
      current.write(paragraph);
    }

    if (current.isNotEmpty) {
      chunks.add(current.toString().trim());
    }

    return chunks;
  }

  List<String> _hardSplit(String text, int maxChars) {
    final pieces = <String>[];
    int start = 0;
    while (start < text.length) {
      final end = (start + maxChars < text.length) ? start + maxChars : text.length;
      pieces.add(text.substring(start, end));
      start = end;
    }
    return pieces;
  }

  String _headline(String chunk) {
    final firstLine = chunk.split('\n').first.trim();
    if (firstLine.isEmpty) return '핵심 절차';
    if (firstLine.length <= 40) return firstLine;
    return '${firstLine.substring(0, 40)}...';
  }

  String _buildDeterministicId(String filePath, int index, String chunk) {
    final digest = sha1.convert(utf8.encode('$filePath::$index::$chunk')).toString();
    return 'manual-${digest.substring(0, 24)}';
  }
}
