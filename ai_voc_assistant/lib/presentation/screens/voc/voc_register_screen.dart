import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/utils/voc_category_catalog.dart';
import '../../../domain/entities/voc_entity.dart';
import '../../viewmodels/voc_viewmodel.dart';
import '../../viewmodels/ai_viewmodel.dart';
import '../../viewmodels/dashboard_viewmodel.dart';
import '../../viewmodels/integration_viewmodel.dart';
import '../../viewmodels/settings_viewmodel.dart';
import 'voc_detail_screen.dart';

class VocRegisterScreen extends StatefulWidget {
  const VocRegisterScreen({super.key});

  @override
  State<VocRegisterScreen> createState() => _VocRegisterScreenState();
}

class _VocRegisterScreenState extends State<VocRegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _contentController = TextEditingController();
  final _customerController = TextEditingController();
  final _vocNumberController = TextEditingController();

  String _selectedProjectCode = '';
  String _selectedBusinessType = '';
  String _selectedProjectName = '';
  bool _isSaving = false;

  @override
  void dispose() {
    _titleController.dispose();
    _contentController.dispose();
    _customerController.dispose();
    _vocNumberController.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final settingsVm = context.read<SettingsViewModel>();
    final codes = settingsVm.projectCodes;
    if (_selectedProjectCode.isEmpty ||
        (_selectedProjectCode.isNotEmpty && !codes.contains(_selectedProjectCode))) {
      _selectedProjectCode = codes.isEmpty ? '' : codes.first;
    }
    final types = settingsVm.businessTypeOptions;
    if (_selectedBusinessType.isEmpty ||
        (_selectedBusinessType.isNotEmpty && !types.contains(_selectedBusinessType))) {
      _selectedBusinessType = types.isEmpty ? '' : types.first;
    }
    final names = settingsVm.projectNameOptions;
    if (_selectedProjectName.isEmpty ||
        (_selectedProjectName.isNotEmpty && !names.contains(_selectedProjectName))) {
      _selectedProjectName = names.isEmpty ? '' : names.first;
    }
  }

  String _buildProjectFieldValue() {
    final projectName = _selectedProjectName.trim();
    final code = _selectedProjectCode.trim().toUpperCase();
    final vocNumber = _vocNumberController.text.trim().toUpperCase();

    final parts = <String>[];
    if (projectName.isNotEmpty) parts.add(projectName);
    if (code.isNotEmpty) parts.add(code);
    if (vocNumber.isNotEmpty) parts.add(vocNumber);
    return parts.join(' | ');
  }

  Future<void> _submit() async {
    if (_isSaving) return;
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSaving = true);
    final vocVm = context.read<VocViewModel>();
    final aiVm = context.read<AiViewModel>();
    final integrationVm = context.read<IntegrationViewModel>();
    final dashboardVm = context.read<DashboardViewModel>();
    final settingsVm = context.read<SettingsViewModel>();
    final messenger = ScaffoldMessenger.of(context);
    final title = _titleController.text.trim();
    final content = _contentController.text.trim();
    late final VocEntity voc;

    try {
      final autoCategory = VocCategoryCatalog.normalize(
        VocCategoryCatalog.fallbackCategory,
        title: title,
        content: content,
      );
      final autoPriority = _priorityFromUrgency(null);
      final autoTags = _buildAutoTags(
        category: autoCategory,
        businessType: _selectedBusinessType,
      );

      voc = await vocVm.createVoc(
        title: title,
        content: content,
        category: autoCategory,
        tags: autoTags,
        customer: _customerController.text.trim(),
        project: _buildProjectFieldValue(),
        businessType: _selectedBusinessType.trim(),
        priority: autoPriority,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _isSaving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('등록 오류: $e'), backgroundColor: Colors.red),
      );
      return;
    }

    if (!mounted) return;

    unawaited(dashboardVm.loadDashboard());
    messenger.showSnackBar(const SnackBar(
      content: Text('VOC가 등록되었습니다. AI 분석은 백그라운드에서 진행합니다.'),
    ));

    // 외부 앱 전송은 화면 전환을 막지 않도록 비동기 처리한다.
    unawaited(
        integrationVm
            .forwardVocToPeerApps(voc)
            .timeout(const Duration(seconds: 8))
            .then((message) {
          if (!mounted || message == null) {
            return;
          }
          messenger.showSnackBar(SnackBar(content: Text(message)));
        }).catchError((_) {
          // 동기화 실패는 IntegrationViewModel의 상태/로그로 확인할 수 있다.
        }),
    );

    unawaited(_runPostRegistrationAi(
      vocVm: vocVm,
      aiVm: aiVm,
      vocId: voc.id,
      title: voc.title,
      content: voc.content,
      autoAnswer: settingsVm.aiAutoAnswerOnVocRegister,
    ));

    setState(() => _isSaving = false);

    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => VocDetailScreen(vocId: voc.id)),
    );
  }

  Future<void> _runPostRegistrationAi({
    required VocViewModel vocVm,
    required AiViewModel aiVm,
    required String vocId,
    required String title,
    required String content,
    required bool autoAnswer,
  }) async {
    try {
      final intelligence = await aiVm.analyzeVocIntelligence(title, content);
      if (intelligence == null) return;

      await vocVm.updateVocWithAiAnalysis(
        vocId,
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

      if (!autoAnswer || !intelligence.isBusiness) return;
      await aiVm.searchSimilarVocs('$title $content');
      final answer = await aiVm.generateAnswer(title, content);
      if (answer == null || answer.answer.trim().isEmpty) return;
      await vocVm.createDraftResponse(
        vocId: vocId,
        content: answer.answer,
        aiGenerated: true,
        confidence: answer.confidence,
        referencedVocIds: aiVm.similarVocs
            .map((item) => item.knowledgeBase.vocId)
            .whereType<String>()
            .toList(),
      );
    } catch (_) {
      // 등록 성공과 후속 AI 처리 실패를 분리한다. 사용자는 상세 화면에서 재실행할 수 있다.
    }
  }

  @override
  Widget build(BuildContext context) {
    final projectCodes = context.watch<SettingsViewModel>().projectCodes;
    final businessTypes = context.watch<SettingsViewModel>().businessTypeOptions;
    final projectNames = context.watch<SettingsViewModel>().projectNameOptions;
    final businessTypeValue = businessTypes.contains(_selectedBusinessType)
        ? _selectedBusinessType
        : (businessTypes.isEmpty ? '' : businessTypes.first);
    final projectNameValue = projectNames.contains(_selectedProjectName)
        ? _selectedProjectName
        : (projectNames.isEmpty ? '' : projectNames.first);

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
              DropdownButtonFormField<String>(
                value: businessTypeValue,
                decoration: const InputDecoration(
                  labelText: '업무 구분 (선택)',
                  prefixIcon: Icon(Icons.work_outline),
                ),
                items: [
                  const DropdownMenuItem(value: '', child: Text('선택 안함')),
                  ...businessTypes.map(
                    (item) => DropdownMenuItem(value: item, child: Text(item)),
                  ),
                ],
                onChanged: (v) => setState(() => _selectedBusinessType = v ?? ''),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: projectNameValue,
                decoration: const InputDecoration(
                  labelText: '프로젝트명 (선택)',
                  prefixIcon: Icon(Icons.folder_outlined),
                ),
                items: [
                  const DropdownMenuItem(value: '', child: Text('선택 안함')),
                  ...projectNames.map(
                    (item) => DropdownMenuItem(value: item, child: Text(item)),
                  ),
                ],
                onChanged: (v) => setState(() => _selectedProjectName = v ?? ''),
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

  String _priorityFromUrgency(String? urgency) {
    final normalized = urgency?.trim().toLowerCase() ?? '';
    if (normalized.contains('high') ||
        normalized.contains('critical') ||
        normalized.contains('긴급')) {
      return AppConstants.priorityHigh;
    }
    if (normalized.contains('low') || normalized.contains('낮')) {
      return AppConstants.priorityLow;
    }
    return AppConstants.priorityMedium;
  }

  String _buildAutoTags({
    required String category,
    String? urgency,
    String? department,
    String? businessType,
  }) {
    final values = <String>{
      category.trim(),
      if (urgency?.trim().isNotEmpty == true) urgency!.trim(),
      if (department?.trim().isNotEmpty == true) department!.trim(),
      if (businessType?.trim().isNotEmpty == true) businessType!.trim(),
    };
    return values.join(', ');
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
