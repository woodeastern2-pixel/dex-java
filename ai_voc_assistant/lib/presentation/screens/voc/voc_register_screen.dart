import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../viewmodels/voc_viewmodel.dart';
import '../../viewmodels/ai_viewmodel.dart';
import '../../viewmodels/dashboard_viewmodel.dart';
import '../../viewmodels/settings_viewmodel.dart';
import 'ai_answer_screen.dart';

class VocRegisterScreen extends StatefulWidget {
  const VocRegisterScreen({super.key});

  @override
  State<VocRegisterScreen> createState() => _VocRegisterScreenState();
}

class _VocRegisterScreenState extends State<VocRegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _contentController = TextEditingController();
  final _tagsController = TextEditingController();
  final _customerController = TextEditingController();
  final _projectController = TextEditingController();
  final _vocNumberController = TextEditingController();

  String _selectedCategory = '';
  String _selectedPriority = AppConstants.priorityMedium;
  String _selectedProjectCode = '';
  bool _isAnalyzing = false;
  bool _isSaving = false;
  String? _analysisPreview;

  @override
  void dispose() {
    _titleController.dispose();
    _contentController.dispose();
    _tagsController.dispose();
    _customerController.dispose();
    _projectController.dispose();
    _vocNumberController.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_selectedCategory.isEmpty) {
      final categories = context.read<SettingsViewModel>().allCategories;
      _selectedCategory = categories.isEmpty
          ? AppConstants.defaultCategories.first
          : categories.first;
    }
    if (_selectedProjectCode.isEmpty) {
      final codes = context.read<SettingsViewModel>().projectCodes;
      _selectedProjectCode = codes.isEmpty ? '' : codes.first;
    }
  }

  String _buildProjectFieldValue() {
    final projectName = _projectController.text.trim();
    final code = _selectedProjectCode.trim().toUpperCase();
    final vocNumber = _vocNumberController.text.trim().toUpperCase();

    final parts = <String>[];
    if (projectName.isNotEmpty) parts.add(projectName);
    if (code.isNotEmpty) parts.add(code);
    if (vocNumber.isNotEmpty) parts.add(vocNumber);
    return parts.join(' | ');
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isAnalyzing = true);
    final aiVm = context.read<AiViewModel>();

    final intelligence = await aiVm.analyzeVocIntelligence(
      _titleController.text.trim(),
      _contentController.text.trim(),
    );

    setState(() => _isAnalyzing = false);

    if (!mounted) return;

    if (intelligence != null) {
      _analysisPreview =
          '업무관련 ${(intelligence.businessScore * 100).toStringAsFixed(0)}%, '
          '카테고리 ${intelligence.category}, '
          '긴급도 ${intelligence.urgency}, '
          '부서 ${intelligence.department}, '
          '담당 ${intelligence.assignee}, '
          '중복 ${(intelligence.duplicateScore * 100).toStringAsFixed(0)}%, '
          'JIRA ${(intelligence.jiraScore * 100).toStringAsFixed(0)}%';
    }

    if (intelligence != null && !intelligence.isBusiness) {
      // 업무 무관 VOC 등록 여부 확인
      final proceed = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('업무 관련 여부 확인'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.warning_amber, color: Colors.orange, size: 40),
              const SizedBox(height: 12),
              const Text('AI 분석 결과: 업무와 무관한 문의입니다.',
                  style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('판단 근거: ${intelligence.reason}'),
              const SizedBox(height: 12),
              const Text('그래도 VOC를 등록하시겠습니까?'),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('취소')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('등록')),
          ],
        ),
      );
      if (proceed != true) return;
    }

    if (intelligence != null &&
        intelligence.duplicateOfVocId != null &&
        intelligence.duplicateScore >= 0.75) {
      final proceedDuplicate = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('중복 가능성 안내'),
          content: Text(
            '기존 VOC와 중복 가능성이 ${(intelligence.duplicateScore * 100).toStringAsFixed(0)}%로 높습니다.\n'
            '그래도 새 VOC로 등록하시겠습니까?',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('취소'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('계속 등록'),
            ),
          ],
        ),
      );
      if (proceedDuplicate != true) return;
    }

    setState(() => _isSaving = true);
    try {
      final vocVm = context.read<VocViewModel>();
      final voc = await vocVm.createVoc(
        title: _titleController.text.trim(),
        content: _contentController.text.trim(),
        category: intelligence?.category ?? _selectedCategory,
        tags: _tagsController.text.trim().isEmpty ? null : _tagsController.text.trim(),
        customer: _customerController.text.trim(),
        project: _buildProjectFieldValue(),
        priority: _selectedPriority,
      );

      if (intelligence != null) {
        await vocVm.updateVocWithAiAnalysis(
          voc.id,
          isBusinessRelated: intelligence.isBusiness,
          aiCategory: intelligence.category,
          businessScore: intelligence.businessScore,
          categoryScore: intelligence.categoryScore,
          urgency: intelligence.urgency,
          urgencyScore: intelligence.urgencyScore,
          department: intelligence.department,
          departmentScore: intelligence.departmentScore,
          assignee: intelligence.assignee,
          assigneeScore: intelligence.assigneeScore,
          duplicateOfVocId: intelligence.duplicateOfVocId,
          duplicateScore: intelligence.duplicateScore,
          jiraRequired: intelligence.jiraRequired,
          jiraScore: intelligence.jiraScore,
          analysisReason: intelligence.reason,
        );
      }

      context.read<DashboardViewModel>().loadDashboard();

      if (!mounted) return;

      final shouldOpenAi = intelligence?.isBusiness == true
          ? await showDialog<bool>(
              context: context,
              builder: (_) => AlertDialog(
                title: const Text('AI 답변 생성'),
                content: const Text(
                  '등록된 VOC를 기준으로 기존 VOC/답변을 종합해 AI 가능 답변을 바로 생성할까요?',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('나중에'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('지금 생성'),
                  ),
                ],
              ),
            ) ??
              false
          : false;

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            shouldOpenAi ? 'VOC 등록 완료, AI 답변 화면으로 이동합니다.' : 'VOC가 등록되었습니다',
          ),
        ),
      );

      if (shouldOpenAi) {
        context.read<AiViewModel>().clearResults();
        Navigator.of(context).pushReplacement(
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
      } else {
        Navigator.pop(context);
      }
    } catch (e) {
      setState(() => _isSaving = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('오류: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsViewModel>().allCategories;
    final projectCodes = context.watch<SettingsViewModel>().projectCodes;
    return Scaffold(
      appBar: AppBar(title: const Text('VOC 등록')),
      body: Form(
        key: _formKey,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildTextField(
                controller: _customerController,
                label: '고객명 (선택)',
                icon: Icons.person_outline,
                required: false,
              ),
              const SizedBox(height: 12),
              _buildTextField(
                controller: _projectController,
                label: '프로젝트명 (선택)',
                icon: Icons.folder_outlined,
                required: false,
              ),
              const SizedBox(height: 12),

              DropdownButtonFormField<String>(
                value: projectCodes.contains(_selectedProjectCode)
                    ? _selectedProjectCode
                    : '',
                decoration: const InputDecoration(
                  labelText: '프로젝트 코드 (선택)',
                  prefixIcon: Icon(Icons.qr_code_2_outlined),
                ),
                items: [
                  const DropdownMenuItem(value: '', child: Text('선택 안함')),
                  ...projectCodes
                      .map((c) => DropdownMenuItem(value: c, child: Text(c))),
                ],
                onChanged: (v) => setState(() => _selectedProjectCode = v ?? ''),
              ),
              const SizedBox(height: 12),

              _buildTextField(
                controller: _vocNumberController,
                label: 'VOC 번호 (선택, 예: 12345)',
                icon: Icons.confirmation_number_outlined,
                required: false,
              ),
              const SizedBox(height: 12),

              _buildTextField(
                controller: _tagsController,
                label: '태그 (선택, 쉼표로 구분)',
                icon: Icons.sell_outlined,
                required: false,
              ),
              const SizedBox(height: 12),

              // 카테고리
              DropdownButtonFormField<String>(
                value: categories.contains(_selectedCategory)
                    ? _selectedCategory
                    : (categories.isEmpty
                        ? AppConstants.defaultCategories.first
                        : categories.first),
                decoration: const InputDecoration(
                  labelText: '카테고리',
                  prefixIcon: Icon(Icons.category_outlined),
                ),
                items: categories
                    .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                    .toList(),
                onChanged: (v) => setState(() => _selectedCategory = v!),
              ),
              const SizedBox(height: 12),

              // 우선순위
              DropdownButtonFormField<String>(
                value: _selectedPriority,
                decoration: const InputDecoration(
                  labelText: '우선순위',
                  prefixIcon: Icon(Icons.flag_outlined),
                ),
                items: const [
                  DropdownMenuItem(value: 'HIGH', child: Text('높음')),
                  DropdownMenuItem(value: 'MEDIUM', child: Text('보통')),
                  DropdownMenuItem(value: 'LOW', child: Text('낮음')),
                ],
                onChanged: (v) => setState(() => _selectedPriority = v!),
              ),
              const SizedBox(height: 12),

              _buildTextField(
                controller: _titleController,
                label: 'VOC 제목 *',
                icon: Icons.title,
                minLength: 4,
              ),
              const SizedBox(height: 12),

              TextFormField(
                controller: _contentController,
                maxLines: 6,
                decoration: const InputDecoration(
                  labelText: 'VOC 내용 *',
                  alignLabelWithHint: true,
                  prefixIcon: Padding(
                    padding: EdgeInsets.only(bottom: 80),
                    child: Icon(Icons.description_outlined),
                  ),
                ),
                validator: (v) =>
                  v == null || v.trim().isEmpty
                    ? 'VOC 내용을 입력해 주세요'
                    : v.trim().length < 10
                      ? 'VOC 내용은 최소 10자 이상 입력해 주세요'
                      : null,
              ),
              const SizedBox(height: 24),

              if (_analysisPreview != null) ...[
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    color: Theme.of(context)
                        .colorScheme
                        .primaryContainer
                        .withOpacity(0.3),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'AI 분석 미리보기: $_analysisPreview',
                        style: const TextStyle(fontSize: 12),
                      ),
                      const SizedBox(height: 6),
                      Consumer<AiViewModel>(
                        builder: (context, aiVm, _) {
                          if (aiVm.topAssignees.isEmpty) {
                            return const Text('담당자 추천 Top3: 데이터 부족', style: TextStyle(fontSize: 12));
                          }
                          final text = aiVm.topAssignees
                              .map((a) => '${a.assignee}(정확도 ${(a.accuracy * 100).toStringAsFixed(0)}%, 처리 ${a.handled}건)')
                              .join(', ');
                          return Text('담당자 추천 Top3: $text', style: const TextStyle(fontSize: 12));
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],

              if (_isAnalyzing)
                const Center(
                  child: Column(
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 8),
                      Text('AI가 VOC를 분석 중입니다...'),
                    ],
                  ),
                )
              else
                FilledButton.icon(
                  onPressed: _isSaving ? null : _submit,
                  icon: const Icon(Icons.save),
                  label: Text(_isSaving ? '저장 중...' : 'VOC 등록'),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    bool required = true,
    int? minLength,
  }) {
    return TextFormField(
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
      ),
      validator: (v) {
        final text = v?.trim() ?? '';
        if (!required && text.isEmpty) return null;
        if (required && text.isEmpty) return '$label을 입력해 주세요';
        if (minLength != null && text.length < minLength) {
          return '$label은 최소 $minLength자 이상 입력해 주세요';
        }
        return null;
      },
    );
  }
}
