class ResponseEntity {
  final String id;
  final String vocId;
  final String content;
  final String status;
  final bool aiGenerated;
  final double? confidenceScore;
  final List<String> referencedVocIds;
  final String? approvedBy;
  final DateTime? approvedAt;
  final DateTime createdAt;
  final DateTime updatedAt;

  const ResponseEntity({
    required this.id,
    required this.vocId,
    required this.content,
    required this.status,
    this.aiGenerated = false,
    this.confidenceScore,
    this.referencedVocIds = const [],
    this.approvedBy,
    this.approvedAt,
    required this.createdAt,
    required this.updatedAt,
  });

  bool get isApproved => status == 'APPROVED';
  bool get isDraft => status == 'DRAFT';

  ResponseEntity copyWith({
    String? content,
    String? status,
    double? confidenceScore,
    List<String>? referencedVocIds,
    String? approvedBy,
    DateTime? approvedAt,
    DateTime? updatedAt,
  }) {
    return ResponseEntity(
      id: id,
      vocId: vocId,
      content: content ?? this.content,
      status: status ?? this.status,
      aiGenerated: aiGenerated,
      confidenceScore: confidenceScore ?? this.confidenceScore,
      referencedVocIds: referencedVocIds ?? this.referencedVocIds,
      approvedBy: approvedBy ?? this.approvedBy,
      approvedAt: approvedAt ?? this.approvedAt,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}
