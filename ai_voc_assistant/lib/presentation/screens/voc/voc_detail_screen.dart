import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../viewmodels/voc_viewmodel.dart';
import '../../viewmodels/ai_viewmodel.dart';
import '../../viewmodels/auth_viewmodel.dart';
import '../../viewmodels/jira_viewmodel.dart';
import '../../viewmodels/dashboard_viewmodel.dart';
import '../../viewmodels/integration_viewmodel.dart';
import '../../viewmodels/settings_viewmodel.dart';
import '../../widgets/voc_status_chip.dart';
import '../../widgets/priority_chip.dart';
import 'ai_answer_screen.dart';

class VocDetailScreen extends StatefulWidget {
  final String vocId;
  const VocDetailScreen({super.key, required this.vocId});

  @override
  State<VocDetailScreen> createState() => _VocDetailScreenState();
}

class _VocDetailScreenState extends State<VocDetailScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<VocViewModel>().selectVoc(widget.vocId);
      context.read<JiraViewModel>().loadLinksForVoc(widget.vocId);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<VocViewModel>(
      builder: (context, vm, _) {
        final voc = vm.selectedVoc;
        if (voc == null || voc.id != widget.vocId) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }

        return Scaffold(
          appBar: AppBar(
            title: Text(voc.title, overflow: TextOverflow.ellipsis),
            actions: [
              PopupMenuButton<String>(
                onSelected: (action) => _handleAction(context, action, vm),
                itemBuilder: (_) => [
                  const PopupMenuItem(value: 'edit', child: Text('수정')),
                  if (voc.status == AppConstants.vocStatusOpen)
                    const PopupMenuItem(
                        value: 'in_progress', child: Text('처리중으로 변경')),
                  if (voc.status != AppConstants.vocStatusResolved)
                    const PopupMenuItem(
                        value: 'resolve', child: Text('해결 완료')),
                  const PopupMenuItem(value: 'delete', child: Text('삭제')),
                ],
              ),
            ],
          ),
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 상태 및 메타정보
                _VocMetaCard(voc: voc),
                const SizedBox(height: 16),

                // VOC 내용
                _SectionCard(
                  title: 'VOC 내용',
                  child: Text(voc.content),
                ),
                const SizedBox(height: 16),

                _IntelligencePanel(voc: voc),
                const SizedBox(height: 16),

                // AI 분석 결과
                if (!voc.isBusinessRelated)
                  _RejectBanner()
                else ...[
                  // AI 답변 추천 버튼
                  _AiAnswerCard(voc: voc),
                  const SizedBox(height: 16),
                ],

                // 답변 목록
                _ResponsesSection(vocId: voc.id, vm: vm, voc: voc),
                const SizedBox(height: 16),

                _CollaborationActions(voc: voc, responses: vm.responses),
                const SizedBox(height: 16),

                // JIRA 연동
                _JiraSection(vocId: voc.id, vocTitle: voc.title, vocContent: voc.content),
                const SizedBox(height: 80),
              ],
            ),
          ),
        );
      },
    );
  }

  void _handleAction(BuildContext context, String action, VocViewModel vm) async {
    final voc = vm.selectedVoc!;
    switch (action) {
      case 'edit':
        await _showEditDialog(context, vm, voc);
        context.read<DashboardViewModel>().loadDashboard();
        break;
      case 'in_progress':
        await vm.updateVocStatus(voc.id, AppConstants.vocStatusInProgress);
        context.read<DashboardViewModel>().loadDashboard();
        break;
      case 'resolve':
        await vm.updateVocStatus(voc.id, AppConstants.vocStatusResolved);
        context.read<DashboardViewModel>().loadDashboard();
        break;
      case 'delete':
        final confirm = await showDialog<bool>(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('VOC 삭제'),
            content: const Text('정말 삭제하시겠습니까?'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(context, false),
                  child: const Text('취소')),
              FilledButton(
                  onPressed: () => Navigator.pop(context, true),
                  child: const Text('삭제')),
            ],
          ),
        );
        if (confirm == true && context.mounted) {
          await vm.deleteVoc(voc.id);
          Navigator.pop(context);
        }
        break;
    }
  }

  Future<void> _showEditDialog(
    BuildContext context,
    VocViewModel vm,
    dynamic voc,
  ) async {
    final titleController = TextEditingController(text: voc.title);
    final contentController = TextEditingController(text: voc.content);
    final customerController = TextEditingController(text: voc.customer);
    final projectController = TextEditingController(text: voc.project);

    final categories = context.read<SettingsViewModel>().allCategories;
    var selectedCategory = categories.contains(voc.category)
        ? voc.category as String
        : categories.first;
    var selectedPriority = voc.priority as String;

    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setState) => AlertDialog(
            title: const Text('VOC 수정'),
            content: SizedBox(
              width: 520,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: customerController,
                      decoration: const InputDecoration(labelText: '고객명'),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: projectController,
                      decoration: const InputDecoration(labelText: '프로젝트명'),
                    ),
                    const SizedBox(height: 8),
                    DropdownButtonFormField<String>(
                      value: selectedCategory,
                      decoration: const InputDecoration(labelText: '카테고리'),
                      items: categories
                          .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                          .toList(),
                      onChanged: (v) {
                        if (v != null) {
                          setState(() => selectedCategory = v);
                        }
                      },
                    ),
                    const SizedBox(height: 8),
                    DropdownButtonFormField<String>(
                      value: selectedPriority,
                      decoration: const InputDecoration(labelText: '우선순위'),
                      items: const [
                        DropdownMenuItem(value: 'HIGH', child: Text('높음')),
                        DropdownMenuItem(value: 'MEDIUM', child: Text('보통')),
                        DropdownMenuItem(value: 'LOW', child: Text('낮음')),
                      ],
                      onChanged: (v) {
                        if (v != null) {
                          setState(() => selectedPriority = v);
                        }
                      },
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: titleController,
                      decoration: const InputDecoration(labelText: '제목'),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: contentController,
                      maxLines: 6,
                      decoration: const InputDecoration(
                        labelText: '내용',
                        alignLabelWithHint: true,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('취소'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('저장'),
              ),
            ],
          ),
        );
      },
    );

    if (saved == true) {
      final title = titleController.text.trim();
      final content = contentController.text.trim();
      final customer = customerController.text.trim();
      final project = projectController.text.trim();
      if (title.isEmpty || content.isEmpty || customer.isEmpty || project.isEmpty) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('제목, 내용, 고객명, 프로젝트명은 비워둘 수 없습니다.'),
              backgroundColor: Colors.red,
            ),
          );
        }
      } else {
        await vm.updateVocFields(
          voc.id,
          title: title,
          content: content,
          category: selectedCategory,
          customer: customer,
          project: project,
          priority: selectedPriority,
        );
      }
    }

    titleController.dispose();
    contentController.dispose();
    customerController.dispose();
    projectController.dispose();
  }
}

class _VocMetaCard extends StatelessWidget {
  final voc;
  const _VocMetaCard({required this.voc});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                VocStatusChip(status: voc.status),
                const SizedBox(width: 8),
                PriorityChip(priority: voc.priority),
                const SizedBox(width: 8),
                Chip(
                  label: Text(voc.category, style: const TextStyle(fontSize: 11)),
                  visualDensity: VisualDensity.compact,
                  padding: EdgeInsets.zero,
                ),
              ],
            ),
            const Divider(height: 16),
            _MetaRow(Icons.person_outline, '고객', voc.customer),
            _MetaRow(Icons.folder_outlined, '프로젝트', voc.project),
            _MetaRow(Icons.access_time, '등록일',
                _formatDate(voc.createdAt)),
            if (voc.aiCategory != null)
              _MetaRow(Icons.auto_awesome, 'AI 분류', voc.aiCategory!),
            if (voc.urgency != null)
              _MetaRow(Icons.priority_high, '긴급도', voc.urgency!),
            if (voc.department != null)
              _MetaRow(Icons.apartment, '담당 부서', voc.department!),
            if (voc.assignee != null)
              _MetaRow(Icons.badge_outlined, '담당자 추천', voc.assignee!),
            if (voc.duplicateScore != null)
              _MetaRow(Icons.copy_all_outlined, '중복 점수',
                  '${(voc.duplicateScore! * 100).toStringAsFixed(0)}%'),
            _MetaRow(Icons.bug_report_outlined, 'JIRA 필요', voc.jiraRequired ? '예' : '아니오'),
          ],
        ),
      ),
    );
  }

  String _formatDate(DateTime dt) {
    return '${dt.year}.${dt.month.toString().padLeft(2, '0')}.${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}

class _MetaRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _MetaRow(this.icon, this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Icon(icon, size: 16, color: Theme.of(context).colorScheme.outline),
          const SizedBox(width: 8),
          Text('$label: ',
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(fontWeight: FontWeight.w500)),
          Expanded(
              child: Text(value,
                  style: Theme.of(context).textTheme.bodySmall)),
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final String title;
  final Widget child;
  const _SectionCard({required this.title, required this.child});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleSmall),
            const Divider(height: 16),
            child,
          ],
        ),
      ),
    );
  }
}

class _RejectBanner extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.red.shade50,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.red.shade200),
      ),
      child: Row(
        children: [
          const Icon(Icons.block, color: Colors.red),
          const SizedBox(width: 8),
          const Text('업무 관련 VOC가 아닙니다.',
              style: TextStyle(color: Colors.red, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}

class _AiAnswerCard extends StatelessWidget {
  final voc;
  const _AiAnswerCard({required this.voc});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.primaryContainer.withOpacity(0.3),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const Icon(Icons.auto_awesome, color: Colors.blue),
            const SizedBox(width: 12),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('AI 답변 추천',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  Text('유사 VOC를 검색하고 AI 답변을 생성합니다',
                      style: TextStyle(fontSize: 12)),
                ],
              ),
            ),
            FilledButton.icon(
              onPressed: () {
                context.read<AiViewModel>().clearResults();
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => AiAnswerScreen(
                      vocId: voc.id,
                      vocTitle: voc.title,
                      vocContent: voc.content,
                      category: voc.category,
                      customer: voc.customer,
                      project: voc.project,
                    ),
                  ),
                );
              },
              icon: const Icon(Icons.auto_awesome, size: 16),
              label: const Text('AI 추천'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ResponsesSection extends StatefulWidget {
  final String vocId;
  final VocViewModel vm;
  final dynamic voc;
  const _ResponsesSection({required this.vocId, required this.vm, required this.voc});

  @override
  State<_ResponsesSection> createState() => _ResponsesSectionState();
}

class _ResponsesSectionState extends State<_ResponsesSection> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final responses = widget.vm.responses;
    final auth = context.watch<AuthViewModel>();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('답변 (${responses.length})',
            style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        ...responses.map((r) => _ResponseCard(
              response: r,
              canApprove: auth.isAdmin && r.isDraft,
              onApprove: () async {
                await widget.vm.approveResponse(r.id, auth.userName);
                // 승인 시 Confluence FAQ 자동 문서화 시도
                await context.read<IntegrationViewModel>().publishApprovedToConfluence(
                      voc: widget.voc,
                      approvedAnswer: r.content,
                    );
              },
            )),
        // 새 답변 작성
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  controller: _controller,
                  maxLines: 4,
                  decoration: const InputDecoration(
                    hintText: '답변을 작성해 주세요...',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 8),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton.icon(
                    onPressed: () async {
                      if (_controller.text.trim().isEmpty) return;
                      await widget.vm.createDraftResponse(
                        vocId: widget.vocId,
                        content: _controller.text.trim(),
                      );
                      _controller.clear();
                    },
                    icon: const Icon(Icons.send, size: 16),
                    label: const Text('답변 등록 (Draft)'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _ResponseCard extends StatelessWidget {
  final response;
  final bool canApprove;
  final VoidCallback? onApprove;
  const _ResponseCard(
      {required this.response, required this.canApprove, this.onApprove});

  @override
  Widget build(BuildContext context) {
    final isApproved = response.status == 'APPROVED';
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      color: isApproved
          ? Colors.green.shade50
          : Theme.of(context).cardColor,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                if (response.aiGenerated)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.blue.shade100,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text('AI',
                        style: TextStyle(fontSize: 10, color: Colors.blue)),
                  ),
                if (response.aiGenerated) const SizedBox(width: 6),
                Chip(
                  label: Text(isApproved ? '승인됨' : 'Draft',
                      style: const TextStyle(fontSize: 10)),
                  visualDensity: VisualDensity.compact,
                  padding: EdgeInsets.zero,
                  backgroundColor: isApproved
                      ? Colors.green.shade100
                      : Colors.orange.shade100,
                ),
                if (response.confidenceScore != null) ...[
                  const SizedBox(width: 8),
                  Text(
                      '신뢰도: ${(response.confidenceScore * 100).toStringAsFixed(0)}%',
                      style: const TextStyle(fontSize: 11, color: Colors.grey)),
                ],
                const Spacer(),
                if (canApprove)
                  TextButton.icon(
                    onPressed: onApprove,
                    icon: const Icon(Icons.check, size: 14),
                    label: const Text('승인', style: TextStyle(fontSize: 12)),
                    style: TextButton.styleFrom(
                      foregroundColor: Colors.green,
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(response.content),
            if (isApproved && response.approvedBy != null) ...[
              const SizedBox(height: 4),
              Text('승인: ${response.approvedBy}',
                  style: const TextStyle(fontSize: 11, color: Colors.grey)),
            ],
          ],
        ),
      ),
    );
  }
}

class _IntelligencePanel extends StatelessWidget {
  final dynamic voc;
  const _IntelligencePanel({required this.voc});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('AI 분석 결과', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _ScoreChip(label: '업무관련', score: voc.businessScore),
                _ScoreChip(label: '카테고리', score: voc.categoryScore),
                _ScoreChip(label: '긴급도', score: voc.urgencyScore, value: voc.urgency),
                _ScoreChip(label: '부서추천', score: voc.departmentScore, value: voc.department),
                _ScoreChip(label: '담당추천', score: voc.assigneeScore, value: voc.assignee),
                _ScoreChip(label: '중복', score: voc.duplicateScore),
                _ScoreChip(label: 'JIRA필요', score: voc.jiraScore),
              ],
            ),
            if (voc.analysisReason != null && '${voc.analysisReason}'.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('근거: ${voc.analysisReason}', style: const TextStyle(fontSize: 12)),
            ],
          ],
        ),
      ),
    );
  }
}

class _ScoreChip extends StatelessWidget {
  final String label;
  final double? score;
  final String? value;
  const _ScoreChip({required this.label, this.score, this.value});

  @override
  Widget build(BuildContext context) {
    final pct = ((score ?? 0) * 100).toStringAsFixed(0);
    return Chip(
      label: Text(
        value == null ? '$label $pct%' : '$label ${value!} ($pct%)',
        style: const TextStyle(fontSize: 11),
      ),
      visualDensity: VisualDensity.compact,
    );
  }
}

class _CollaborationActions extends StatelessWidget {
  final dynamic voc;
  final List<dynamic> responses;
  const _CollaborationActions({required this.voc, required this.responses});

  @override
  Widget build(BuildContext context) {
    final integration = context.watch<IntegrationViewModel>();
    final latestAnswer = responses.isNotEmpty ? responses.first.content.toString() : '';
    final urgent = (voc.urgency ?? '').toString().toLowerCase();
    final isUrgent = urgent == 'critical' || urgent == 'high';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('협업 연동', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: !isUrgent || integration.isLoading
                        ? null
                        : () => context.read<IntegrationViewModel>().notifyUrgentVocToTeams(voc),
                    icon: const Icon(Icons.notifications_active_outlined),
                    label: const Text('Teams 긴급 알림'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: latestAnswer.isEmpty || integration.isLoading
                        ? null
                        : () => context.read<IntegrationViewModel>().shareAiAnswerToTeams(
                              voc: voc,
                              answer: latestAnswer,
                            ),
                    icon: const Icon(Icons.share_outlined),
                    label: const Text('Teams 답변 공유'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: integration.isLoading
                        ? null
                        : () => context.read<IntegrationViewModel>().shareVocToSlack(voc: voc),
                    icon: const Icon(Icons.forum_outlined),
                    label: const Text('Slack VOC 공유'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: latestAnswer.isEmpty || integration.isLoading
                        ? null
                        : () => context.read<IntegrationViewModel>().shareAiAnswerToSlack(
                              voc: voc,
                              answer: latestAnswer,
                            ),
                    icon: const Icon(Icons.chat_bubble_outline),
                    label: const Text('Slack 답변 공유'),
                  ),
                ),
              ],
            ),
            if (integration.error != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(integration.error!, style: const TextStyle(color: Colors.red, fontSize: 12)),
              ),
            if (integration.success != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(integration.success!, style: const TextStyle(color: Colors.green, fontSize: 12)),
              ),
          ],
        ),
      ),
    );
  }
}

class _JiraSection extends StatelessWidget {
  final String vocId;
  final String vocTitle;
  final String vocContent;
  const _JiraSection({
    required this.vocId,
    required this.vocTitle,
    required this.vocContent,
  });

  @override
  Widget build(BuildContext context) {
    return Consumer<JiraViewModel>(
      builder: (context, jiraVm, _) {
        if (!jiraVm.isConfigured) {
          return const SizedBox.shrink();
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('JIRA 연동',
                    style: Theme.of(context).textTheme.titleSmall),
                const Spacer(),
                OutlinedButton.icon(
                  onPressed: jiraVm.isLoading
                      ? null
                      : () => _createJiraIssue(context, jiraVm),
                  icon: const Icon(Icons.add, size: 14),
                  label: const Text('이슈 생성', style: TextStyle(fontSize: 12)),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ...jiraVm.vocLinks.map((link) => Card(
                  child: ListTile(
                    leading: const Icon(Icons.link),
                    title: Text(link.jiraKey),
                    subtitle: Text(link.jiraSummary ?? ''),
                    trailing: Text(link.jiraStatus ?? '',
                        style: const TextStyle(fontSize: 12)),
                  ),
                )),
          ],
        );
      },
    );
  }

  void _createJiraIssue(BuildContext context, JiraViewModel jiraVm) async {
    final result = await jiraVm.createIssueForVoc(
      vocId: vocId,
      summary: '[VOC] $vocTitle',
      description: vocContent,
    );
    if (context.mounted) {
      if (result != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('JIRA 이슈 ${result.jiraKey} 생성 완료')),
        );
      } else if (jiraVm.error != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(jiraVm.error!),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }
}
