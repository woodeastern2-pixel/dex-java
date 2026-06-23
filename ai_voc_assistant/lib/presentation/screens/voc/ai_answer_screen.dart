import 'package:flutter/material.dart';
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
  final _editController = TextEditingController();
  bool _isEditing = false;
  bool _saved = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _runAiPipeline());
  }

  @override
  void dispose() {
    _editController.dispose();
    super.dispose();
  }

  Future<void> _runAiPipeline() async {
    final aiVm = context.read<AiViewModel>();
    // 1. 유사 VOC + 기존 답변 검색
    await aiVm.searchSimilarVocs('${widget.vocTitle} ${widget.vocContent}');
    // 2. 답변 생성
    final result = await aiVm.generateAnswer(widget.vocTitle, widget.vocContent);
    if (result != null) {
      _editController.text = result.answer;
    }
  }

  Future<void> _saveDraft() async {
    final aiVm = context.read<AiViewModel>();
    final vocVm = context.read<VocViewModel>();
    final answer = _editController.text.trim();
    if (answer.isEmpty) return;

    // Draft로 저장
    await vocVm.createDraftResponse(
      vocId: widget.vocId,
      content: answer,
      aiGenerated: true,
      confidence: aiVm.answerResult?.confidence,
      referencedVocIds: aiVm.similarVocs
          .map((s) => s.knowledgeBase.id)
          .toList(),
    );

    // 지식베이스에도 저장
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
        const SnackBar(content: Text('답변이 Draft로 저장되고 지식베이스에 등록되었습니다')),
      );
      Navigator.pop(context);
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
                  controller: _editController,
                  isEditing: _isEditing,
                  onEditToggle: () =>
                      setState(() => _isEditing = !_isEditing),
                  onRegenerate: _runAiPipeline,
                ),
                const SizedBox(height: 24),

                // 저장 버튼
                if (vm.hasAnswer && !_saved)
                  FilledButton.icon(
                    onPressed: _saveDraft,
                    icon: const Icon(Icons.save),
                    label: const Text('Draft로 저장 및 지식베이스 등록'),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _SimilarVocsSection extends StatelessWidget {
  final AiViewModel vm;
  const _SimilarVocsSection({required this.vm});

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
  final TextEditingController controller;
  final bool isEditing;
  final VoidCallback onEditToggle;
  final VoidCallback onRegenerate;
  const _AiAnswerSection({
    required this.vm,
    required this.controller,
    required this.isEditing,
    required this.onEditToggle,
    required this.onRegenerate,
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
              icon: const Icon(Icons.refresh, size: 18),
              tooltip: '재생성',
              onPressed: vm.isGenerating ? null : onRegenerate,
            ),
            IconButton(
              icon: Icon(isEditing ? Icons.check : Icons.edit, size: 18),
              tooltip: isEditing ? '완료' : '편집',
              onPressed: onEditToggle,
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
            child: Text(vm.error!, style: const TextStyle(color: Colors.red)),
          )
        else
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: isEditing
                  ? TextField(
                      controller: controller,
                      maxLines: null,
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        hintText: '답변 내용...',
                      ),
                    )
                  : Text(
                      controller.text.isNotEmpty
                          ? controller.text
                          : '답변 생성 중...',
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
