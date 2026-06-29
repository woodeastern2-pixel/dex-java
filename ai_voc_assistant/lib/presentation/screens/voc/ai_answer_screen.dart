import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../viewmodels/ai_viewmodel.dart';
import '../../viewmodels/voc_viewmodel.dart';

class AiAnswerScreen extends StatefulWidget {
  final String vocId;
  final String vocTitle;
  final String vocContent;
  final String category;
  final String customer;
  final String project;

  const AiAnswerScreen({
    super.key,
    required this.vocId,
    required this.vocTitle,
    required this.vocContent,
    required this.category,
    required this.customer,
    required this.project,
  });

  @override
  State<AiAnswerScreen> createState() => _AiAnswerScreenState();
}

class _AiAnswerScreenState extends State<AiAnswerScreen> {
  final _feedbackController = TextEditingController();
  String _generatedAnswer = '';
  bool _isAdopting = false;
  bool _saved = false;
  String _feedbackType = 'useful';
  bool _isSubmittingFeedback = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _runAiPipeline());
  }

  @override
  void dispose() {
    _feedbackController.dispose();
    super.dispose();
  }

  Future<void> _runAiPipeline() async {
    final aiVm = context.read<AiViewModel>();
    // 1. 유사 VOC + 기존 답변 검색
    await aiVm.searchSimilarVocs('${widget.vocTitle} ${widget.vocContent}');
    // 2. 답변 생성
    final result = await aiVm.generateAnswer(widget.vocTitle, widget.vocContent);
    if (result != null) {
      setState(() => _generatedAnswer = result.answer);
    }
  }

  Future<void> _adoptAnswer() async {
    final aiVm = context.read<AiViewModel>();
    final vocVm = context.read<VocViewModel>();
    final answer = _generatedAnswer.trim();
    if (answer.isEmpty) return;

    setState(() => _isAdopting = true);
    try {
      await vocVm.adoptAiAnswer(
        vocId: widget.vocId,
        content: answer,
        confidence: aiVm.answerResult?.confidence,
        referencedVocIds: aiVm.similarVocs
            .map((s) => s.knowledgeBase.id)
            .toList(),
      );

      await aiVm.saveToKnowledgeBase(
        question: widget.vocTitle,
        answer: answer,
        category: widget.category,
        customer: widget.customer,
        project: widget.project,
        vocId: widget.vocId,
      );

      setState(() => _saved = true);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('AI 답변을 채택해 VOC 답변으로 저장했습니다')),
        );
        Navigator.of(context).pop(true);
      }
    } finally {
      if (mounted) {
        setState(() => _isAdopting = false);
      }
    }
  }

  Future<void> _submitFeedback() async {
    final note = _feedbackController.text.trim();
    setState(() => _isSubmittingFeedback = true);
    try {
      await context.read<VocViewModel>().recordAiFeedback(
            vocId: widget.vocId,
            feedbackType: _feedbackType,
            note: note.isEmpty ? null : note,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('AI 피드백이 저장되었습니다')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmittingFeedback = false);
      }
    }
  }

  Future<void> _copyToClipboard(String text, String message) async {
    if (text.trim().isEmpty) return;
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI 답변 추천'),
      ),
      body: Consumer<AiViewModel>(
        builder: (context, vm, _) {
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // VOC 요약
                Card(
                  color: Theme.of(context)
                      .colorScheme
                      .primaryContainer
                      .withOpacity(0.3),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('VOC',
                            style: Theme.of(context).textTheme.labelLarge),
                        const SizedBox(height: 4),
                        Text(widget.vocTitle,
                            style:
                                const TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 4),
                        Text(widget.vocContent,
                            style: const TextStyle(fontSize: 13),
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // 유사 VOC 검색 결과
                _SimilarVocsSection(vm: vm),
                const SizedBox(height: 16),

                // AI 답변 생성
                _AiAnswerSection(
                  vm: vm,
                  answerText: _generatedAnswer,
                  onRegenerate: _runAiPipeline,
                  onCopyAnswer: () => _copyToClipboard(
                    _generatedAnswer,
                    'AI 추천 답변을 복사했습니다',
                  ),
                  onCopyError: vm.error == null
                      ? null
                      : () => _copyToClipboard(
                            vm.error!,
                            '오류 메시지를 복사했습니다',
                          ),
                ),
                const SizedBox(height: 24),

                _AnswerEvidenceSection(vm: vm),
                const SizedBox(height: 16),

                _FeedbackSection(
                  feedbackType: _feedbackType,
                  feedbackController: _feedbackController,
                  isSubmitting: _isSubmittingFeedback,
                  onTypeChanged: (value) => setState(() => _feedbackType = value),
                  onSubmit: _submitFeedback,
                ),
                const SizedBox(height: 24),

                // 저장 버튼
                if (vm.hasAnswer && !_saved)
                  FilledButton.icon(
                    onPressed: _isAdopting ? null : _adoptAnswer,
                    icon: _isAdopting
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.check_circle_outline),
                    label: const Text('AI 답변 채택'),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _AnswerEvidenceSection extends StatelessWidget {
  final AiViewModel vm;

  const _AnswerEvidenceSection({required this.vm});

  @override
  Widget build(BuildContext context) {
    final answer = vm.answerResult;
    if (answer == null && vm.similarVocs.isEmpty) {
      return const SizedBox.shrink();
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('답변 근거', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            if (answer?.notes.isNotEmpty == true) ...[
              Text(
                answer!.notes,
                style: const TextStyle(fontSize: 12, height: 1.5),
              ),
              const SizedBox(height: 8),
            ],
            if (answer?.referencedCases.isNotEmpty == true) ...[
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: answer!.referencedCases
                    .map(
                      (item) => Chip(
                        label: Text(item, overflow: TextOverflow.ellipsis),
                      ),
                    )
                    .toList(),
              ),
              const SizedBox(height: 8),
            ],
            Text(
              '상위 후보 ${vm.similarVocs.length}건을 재랭킹해 답변을 생성했습니다.',
              style: const TextStyle(fontSize: 11, color: Colors.grey),
            ),
            if (vm.similarVocs.isNotEmpty) ...[
              const SizedBox(height: 8),
              ...vm.similarVocs.take(3).map(
                    (item) {
                      final adoptionCount = item.adoptionCount ?? 0;
                      final usageCount = item.usageCount ?? 0;
                      final adoptionRate = usageCount > 0
                          ? (adoptionCount / usageCount) * 100
                          : 0.0;
                        final weightedConfidence =
                          (item.similarityScore * 100 * 0.6) +
                            (adoptionRate * 0.4);
                      final recent = item.lastUsedAt;
                      final recentText = recent == null
                          ? '기록 없음'
                          : '${recent.year}-${recent.month.toString().padLeft(2, '0')}-${recent.day.toString().padLeft(2, '0')}';

                      return Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Text(
                          '- 유사도 ${(item.similarityScore * 100).toStringAsFixed(1)}% | 채택률 ${adoptionRate.toStringAsFixed(1)}% | 최근사용일 $recentText | 신뢰도 ${weightedConfidence.clamp(0, 99.9).toStringAsFixed(1)}%',
                          style: const TextStyle(fontSize: 11, color: Colors.black54),
                        ),
                      );
                    },
                  ),
            ],
          ],
        ),
      ),
    );
  }
}

class _FeedbackSection extends StatelessWidget {
  final String feedbackType;
  final TextEditingController feedbackController;
  final bool isSubmitting;
  final ValueChanged<String> onTypeChanged;
  final VoidCallback onSubmit;

  const _FeedbackSection({
    required this.feedbackType,
    required this.feedbackController,
    required this.isSubmitting,
    required this.onTypeChanged,
    required this.onSubmit,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('AI 피드백', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('도움됨'),
                  selected: feedbackType == 'useful',
                  onSelected: (_) => onTypeChanged('useful'),
                ),
                ChoiceChip(
                  label: const Text('부분적'),
                  selected: feedbackType == 'partial',
                  onSelected: (_) => onTypeChanged('partial'),
                ),
                ChoiceChip(
                  label: const Text('부정확'),
                  selected: feedbackType == 'wrong',
                  onSelected: (_) => onTypeChanged('wrong'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: feedbackController,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: '근거가 부족한 부분이나 보완 포인트를 적어 주세요',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: isSubmitting ? null : onSubmit,
                icon: isSubmitting
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.feedback_outlined, size: 16),
                label: const Text('피드백 저장'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SimilarVocsSection extends StatelessWidget {
  final AiViewModel vm;
  const _SimilarVocsSection({required this.vm});

  Future<void> _copyExistingAnswer(BuildContext context, String answer) async {
    await Clipboard.setData(ClipboardData(text: answer));
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('기존 답변을 복사했습니다')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.search, size: 18),
            const SizedBox(width: 8),
            Text('유사 VOC/기존 답변 검색 결과',
                style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(width: 8),
            if (vm.isSearching)
              const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
          ],
        ),
        const SizedBox(height: 8),
        if (vm.similarVocs.isEmpty && !vm.isSearching)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.orange.shade50,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Text('유사 VOC를 찾지 못했습니다. 더 많은 지식베이스 데이터가 필요합니다.'),
          )
        else
          ...vm.similarVocs.map((r) => Card(
                margin: const EdgeInsets.only(bottom: 6),
                child: ExpansionTile(
                  title: Text(
                    r.knowledgeBase.question,
                    style: const TextStyle(fontSize: 13),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: _ScoreBadge(score: r.similarityScore),
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('답변:',
                              style:
                                  TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                          const SizedBox(height: 4),
                          Text(r.knowledgeBase.answer,
                              style: const TextStyle(fontSize: 12)),
                          const SizedBox(height: 8),
                          Align(
                            alignment: Alignment.centerRight,
                            child: OutlinedButton.icon(
                              onPressed: () =>
                                  _copyExistingAnswer(context, r.knowledgeBase.answer),
                              icon: const Icon(Icons.content_copy, size: 16),
                              label: const Text('답변 복사'),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              )),
      ],
    );
  }
}

class _ScoreBadge extends StatelessWidget {
  final double score;
  const _ScoreBadge({required this.score});

  @override
  Widget build(BuildContext context) {
    final pct = (score * 100).toStringAsFixed(0);
    final color = score >= 0.7
        ? Colors.green
        : score >= 0.5
            ? Colors.orange
            : Colors.grey;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.4)),
      ),
      child: Text(
        '$pct%',
        style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }
}

class _AiAnswerSection extends StatelessWidget {
  final AiViewModel vm;
  final String answerText;
  final VoidCallback onRegenerate;
  final VoidCallback onCopyAnswer;
  final VoidCallback? onCopyError;
  const _AiAnswerSection({
    required this.vm,
    required this.answerText,
    required this.onRegenerate,
    required this.onCopyAnswer,
    this.onCopyError,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.auto_awesome, size: 18, color: Colors.blue),
            const SizedBox(width: 8),
            Text('AI 추천 답변', style: Theme.of(context).textTheme.titleSmall),
            const Spacer(),
            if (vm.answerResult != null)
              _ScoreBadge(score: vm.answerResult!.confidence),
            const SizedBox(width: 8),
            IconButton(
              icon: const Icon(Icons.content_copy, size: 18),
              tooltip: '답변 복사',
              onPressed: answerText.trim().isEmpty ? null : onCopyAnswer,
            ),
            IconButton(
              icon: const Icon(Icons.refresh, size: 18),
              tooltip: '재생성',
              onPressed: vm.isGenerating ? null : onRegenerate,
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (vm.isGenerating)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(
              child: Column(
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 12),
                  Text('AI가 답변을 생성 중입니다...'),
                ],
              ),
            ),
          )
        else if (vm.error != null)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.red.shade50,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    vm.error!,
                    style: const TextStyle(color: Colors.red),
                  ),
                ),
                const SizedBox(width: 8),
                TextButton.icon(
                  onPressed: onCopyError,
                  icon: const Icon(Icons.copy_all_outlined, size: 16),
                  label: const Text('오류 복사'),
                ),
              ],
            ),
          )
        else
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Text(
                answerText.isNotEmpty ? answerText : '답변 생성 중...',
                style: const TextStyle(height: 1.6),
              ),
            ),
          ),
        if (vm.answerResult?.referencedCases.isNotEmpty == true)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              '참고 사례: ${vm.answerResult!.referencedCases.join(', ')}',
              style: const TextStyle(fontSize: 11, color: Colors.grey),
            ),
          ),
      ],
    );
  }
}
