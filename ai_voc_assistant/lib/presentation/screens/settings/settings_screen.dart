import 'dart:io';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../viewmodels/integration_viewmodel.dart';
import '../../viewmodels/settings_viewmodel.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('설정'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'AI 설정'),
            Tab(text: 'JIRA 설정'),
            Tab(text: '연동'),
            Tab(text: '일반'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: const [
          _AiSettingsTab(),
          _JiraSettingsTab(),
          _IntegrationSettingsTab(),
          _GeneralSettingsTab(),
        ],
      ),
    );
  }
}

class _AiSettingsTab extends StatefulWidget {
  const _AiSettingsTab();

  @override
  State<_AiSettingsTab> createState() => _AiSettingsTabState();
}

class _AiSettingsTabState extends State<_AiSettingsTab> {
  final _ollamaUrlController = TextEditingController();
  final _ollamaModelController = TextEditingController();
  final _openAiKeyController = TextEditingController();
  final _openAiModelController = TextEditingController();
  final _geminiKeyController = TextEditingController();
  final _geminiModelController = TextEditingController();
  final _faissEndpointController = TextEditingController();
  String _provider = AppConstants.aiProviderOllama;
  bool _obscureKey = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadSettings());
  }

  void _loadSettings() {
    final vm = context.read<SettingsViewModel>();
    _provider = vm.aiProvider;
    _ollamaUrlController.text = vm.ollamaUrl;
    _ollamaModelController.text = vm.ollamaModel;
    _openAiKeyController.text = vm.openAiKey;
    _openAiModelController.text = vm.openAiModel;
    _geminiKeyController.text = vm.geminiKey;
    _geminiModelController.text = vm.geminiModel;
    _faissEndpointController.text = vm.faissEndpoint;
    setState(() {});
  }

  Future<void> _save() async {
    final vm = context.read<SettingsViewModel>();
    await vm.saveSettings({
      AppConstants.settingAiProvider: _provider,
      AppConstants.settingOllamaUrl: _ollamaUrlController.text.trim(),
      AppConstants.settingOllamaModel: _ollamaModelController.text.trim(),
      AppConstants.settingOpenAiKey: _openAiKeyController.text.trim(),
      AppConstants.settingOpenAiModel: _openAiModelController.text.trim(),
      AppConstants.settingGeminiKey: _geminiKeyController.text.trim(),
      AppConstants.settingGeminiModel: _geminiModelController.text.trim(),
      AppConstants.settingFaissEndpoint: _faissEndpointController.text.trim(),
    });
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('AI 설정이 저장되었습니다')),
      );
    }
  }

  @override
  void dispose() {
    _ollamaUrlController.dispose();
    _ollamaModelController.dispose();
    _openAiKeyController.dispose();
    _openAiModelController.dispose();
    _geminiKeyController.dispose();
    _geminiModelController.dispose();
    _faissEndpointController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('AI 제공자', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 12),
                  RadioListTile<String>(
                    title: const Text('Ollama (로컬)'),
                    subtitle: const Text('서버 없이 로컬에서 실행'),
                    value: AppConstants.aiProviderOllama,
                    groupValue: _provider,
                    onChanged: (v) => setState(() => _provider = v!),
                    dense: true,
                  ),
                  RadioListTile<String>(
                    title: const Text('OpenAI API'),
                    subtitle: const Text('GPT 모델 사용 (API Key 필요)'),
                    value: AppConstants.aiProviderOpenAi,
                    groupValue: _provider,
                    onChanged: (v) => setState(() => _provider = v!),
                    dense: true,
                  ),
                  RadioListTile<String>(
                    title: const Text('Google Gemini API'),
                    subtitle: const Text('Gemini 모델 사용 (API Key 필요)'),
                    value: AppConstants.aiProviderGemini,
                    groupValue: _provider,
                    onChanged: (v) => setState(() => _provider = v!),
                    dense: true,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          if (_provider == AppConstants.aiProviderOllama)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Ollama 설정',
                        style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _ollamaUrlController,
                      decoration: const InputDecoration(
                        labelText: 'Ollama URL',
                        hintText: 'http://localhost:11434',
                        prefixIcon: Icon(Icons.dns_outlined),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _ollamaModelController,
                      decoration: const InputDecoration(
                        labelText: '모델명',
                        hintText: 'llama3.2',
                        prefixIcon: Icon(Icons.smart_toy_outlined),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          if (_provider == AppConstants.aiProviderOpenAi)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('OpenAI 설정',
                        style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _openAiKeyController,
                      obscureText: _obscureKey,
                      decoration: InputDecoration(
                        labelText: 'API Key',
                        hintText: 'sk-...',
                        prefixIcon: const Icon(Icons.key_outlined),
                        suffixIcon: IconButton(
                          icon: Icon(_obscureKey
                              ? Icons.visibility_off
                              : Icons.visibility),
                          onPressed: () =>
                              setState(() => _obscureKey = !_obscureKey),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _openAiModelController,
                      decoration: const InputDecoration(
                        labelText: '모델',
                        hintText: 'gpt-4o-mini',
                        prefixIcon: Icon(Icons.smart_toy_outlined),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          if (_provider == AppConstants.aiProviderGemini)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Gemini 설정',
                        style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _geminiKeyController,
                      obscureText: _obscureKey,
                      decoration: InputDecoration(
                        labelText: 'API Key',
                        hintText: 'AIza...',
                        prefixIcon: const Icon(Icons.key_outlined),
                        suffixIcon: IconButton(
                          icon: Icon(_obscureKey
                              ? Icons.visibility_off
                              : Icons.visibility),
                          onPressed: () =>
                              setState(() => _obscureKey = !_obscureKey),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _geminiModelController,
                      decoration: const InputDecoration(
                        labelText: '모델',
                        hintText: 'gemini-1.5-flash',
                        prefixIcon: Icon(Icons.auto_awesome_outlined),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('FAISS 브릿지 (선택)',
                      style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 8),
                  const Text(
                    '비워두면 앱 내부 코사인 검색을 사용합니다.',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _faissEndpointController,
                    decoration: const InputDecoration(
                      labelText: 'FAISS Endpoint',
                      hintText: 'http://127.0.0.1:8787',
                      prefixIcon: Icon(Icons.hub_outlined),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _save,
            icon: const Icon(Icons.save),
            label: const Text('AI 설정 저장'),
          ),
        ],
      ),
    );
  }
}

class _JiraSettingsTab extends StatefulWidget {
  const _JiraSettingsTab();

  @override
  State<_JiraSettingsTab> createState() => _JiraSettingsTabState();
}

class _JiraSettingsTabState extends State<_JiraSettingsTab> {
  final _urlController = TextEditingController();
  final _projectKeyController = TextEditingController();
  final _emailController = TextEditingController();
  final _tokenController = TextEditingController();
  bool _obscureToken = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadSettings());
  }

  void _loadSettings() {
    final vm = context.read<SettingsViewModel>();
    _urlController.text = vm.jiraUrl;
    _projectKeyController.text = vm.jiraProjectKey;
    _emailController.text = vm.jiraEmail;
    _tokenController.text = vm.jiraToken;
    setState(() {});
  }

  @override
  void dispose() {
    _urlController.dispose();
    _projectKeyController.dispose();
    _emailController.dispose();
    _tokenController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final vm = context.read<SettingsViewModel>();
    await vm.saveSettings({
      AppConstants.settingJiraUrl: _urlController.text.trim(),
      AppConstants.settingJiraProjectKey: _projectKeyController.text.trim(),
      AppConstants.settingJiraEmail: _emailController.text.trim(),
      AppConstants.settingJiraToken: _tokenController.text.trim(),
    });
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('JIRA 설정이 저장되었습니다')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('JIRA 연동 설정',
                      style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 4),
                  const Text(
                    'JIRA Cloud 또는 Server/DC와 연동합니다.',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _urlController,
                    decoration: const InputDecoration(
                      labelText: 'JIRA URL',
                      hintText: 'https://yourcompany.atlassian.net',
                      prefixIcon: Icon(Icons.link),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _projectKeyController,
                    decoration: const InputDecoration(
                      labelText: '프로젝트 키',
                      hintText: 'VOC',
                      prefixIcon: Icon(Icons.folder_outlined),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _emailController,
                    decoration: const InputDecoration(
                      labelText: '이메일 (Atlassian 계정)',
                      hintText: 'user@company.com',
                      prefixIcon: Icon(Icons.email_outlined),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _tokenController,
                    obscureText: _obscureToken,
                    decoration: InputDecoration(
                      labelText: 'API Token',
                      hintText: 'Atlassian API 토큰',
                      prefixIcon: const Icon(Icons.vpn_key_outlined),
                      suffixIcon: IconButton(
                        icon: Icon(_obscureToken
                            ? Icons.visibility_off
                            : Icons.visibility),
                        onPressed: () =>
                            setState(() => _obscureToken = !_obscureToken),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('API Token 발급 방법',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  Text(
                    '1. Atlassian 계정 로그인\n'
                    '2. Account Settings > Security\n'
                    '3. API Token > Create and manage API tokens\n'
                    '4. Create API token 클릭 후 토큰 복사',
                    style: TextStyle(fontSize: 12, height: 1.6),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _save,
            icon: const Icon(Icons.save),
            label: const Text('JIRA 설정 저장'),
          ),
        ],
      ),
    );
  }
}

class _IntegrationSettingsTab extends StatefulWidget {
  const _IntegrationSettingsTab();

  @override
  State<_IntegrationSettingsTab> createState() => _IntegrationSettingsTabState();
}

class _IntegrationSettingsTabState extends State<_IntegrationSettingsTab> {
  final _outlookTokenController = TextEditingController();
  final _outlookFolderController = TextEditingController();
  final _teamsWebhookController = TextEditingController();
  final _slackWebhookController = TextEditingController();
  final _confluenceUrlController = TextEditingController();
  final _confluenceSpaceController = TextEditingController();
  final _confluenceEmailController = TextEditingController();
  final _confluenceTokenController = TextEditingController();

  bool _obscureOutlook = true;
  bool _obscureConf = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final s = context.read<SettingsViewModel>();
      _outlookTokenController.text = s.outlookAccessToken;
      _outlookFolderController.text = s.outlookFolder;
      _teamsWebhookController.text = s.teamsWebhook;
      _slackWebhookController.text = s.slackWebhook;
      _confluenceUrlController.text = s.confluenceUrl;
      _confluenceSpaceController.text = s.confluenceSpace;
      _confluenceEmailController.text = s.confluenceEmail;
      _confluenceTokenController.text = s.confluenceToken;
      setState(() {});
    });
  }

  @override
  void dispose() {
    _outlookTokenController.dispose();
    _outlookFolderController.dispose();
    _teamsWebhookController.dispose();
    _slackWebhookController.dispose();
    _confluenceUrlController.dispose();
    _confluenceSpaceController.dispose();
    _confluenceEmailController.dispose();
    _confluenceTokenController.dispose();
    super.dispose();
  }

  Future<void> _saveSettings() async {
    final s = context.read<SettingsViewModel>();
    await s.saveSettings({
      AppConstants.settingOutlookAccessToken: _outlookTokenController.text.trim(),
      AppConstants.settingOutlookFolder: _outlookFolderController.text.trim().isEmpty
          ? 'Inbox'
          : _outlookFolderController.text.trim(),
      AppConstants.settingTeamsWebhook: _teamsWebhookController.text.trim(),
      AppConstants.settingSlackWebhook: _slackWebhookController.text.trim(),
      AppConstants.settingConfluenceUrl: _confluenceUrlController.text.trim(),
      AppConstants.settingConfluenceSpace: _confluenceSpaceController.text.trim(),
      AppConstants.settingConfluenceEmail: _confluenceEmailController.text.trim(),
      AppConstants.settingConfluenceToken: _confluenceTokenController.text.trim(),
    });
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('연동 설정이 저장되었습니다')),
      );
    }
  }

  Future<void> _importExcel() async {
    // Note: File picker excluded for build compatibility
    // In production, integrate external file picker library
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('엑셀 Import 기능은 데모에서 미포함입니다. 데이터는 API 연동으로 처리됩니다.')),
    );
  }

  Future<void> _exportExcel() async {
    final dir = await getApplicationDocumentsDirectory();
    final filePath = p.join(
      dir.path,
      'voc_export_${DateTime.now().millisecondsSinceEpoch}.xlsx',
    );

    final vm = context.read<IntegrationViewModel>();
    final out = await vm.exportVocToExcel(filePath);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(out == null ? 'Export 실패' : 'Export 완료: $out')),
    );
  }

  Future<void> _collectOutlook() async {
    final vm = context.read<IntegrationViewModel>();
    final count = await vm.collectOutlookAndCreateVoc(top: 20);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Outlook 메일 VOC 생성: $count건')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<IntegrationViewModel>(
      builder: (context, vm, _) {
        return SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Outlook 연동', style: Theme.of(context).textTheme.titleSmall),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _outlookTokenController,
                        obscureText: _obscureOutlook,
                        decoration: InputDecoration(
                          labelText: 'Graph Access Token',
                          prefixIcon: const Icon(Icons.key_outlined),
                          suffixIcon: IconButton(
                            icon: Icon(_obscureOutlook ? Icons.visibility_off : Icons.visibility),
                            onPressed: () => setState(() => _obscureOutlook = !_obscureOutlook),
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _outlookFolderController,
                        decoration: const InputDecoration(
                          labelText: '메일 폴더',
                          hintText: 'Inbox',
                          prefixIcon: Icon(Icons.inbox_outlined),
                        ),
                      ),
                      const SizedBox(height: 8),
                      OutlinedButton.icon(
                        onPressed: vm.isLoading ? null : _collectOutlook,
                        icon: const Icon(Icons.mail_outline),
                        label: const Text('메일 수집 후 VOC 생성'),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Excel 연동', style: Theme.of(context).textTheme.titleSmall),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed: vm.isLoading ? null : _importExcel,
                              icon: const Icon(Icons.upload_file),
                              label: const Text('VOC Import (xlsx)'),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed: vm.isLoading ? null : _exportExcel,
                              icon: const Icon(Icons.download),
                              label: const Text('VOC/답변 Export'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Teams / Slack Webhook', style: Theme.of(context).textTheme.titleSmall),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _teamsWebhookController,
                        decoration: const InputDecoration(
                          labelText: 'Teams Webhook URL',
                          prefixIcon: Icon(Icons.notifications_active_outlined),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _slackWebhookController,
                        decoration: const InputDecoration(
                          labelText: 'Slack Webhook URL',
                          prefixIcon: Icon(Icons.forum_outlined),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Confluence 연동', style: Theme.of(context).textTheme.titleSmall),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _confluenceUrlController,
                        decoration: const InputDecoration(
                          labelText: 'Confluence URL',
                          prefixIcon: Icon(Icons.link_outlined),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _confluenceSpaceController,
                        decoration: const InputDecoration(
                          labelText: 'Space Key',
                          prefixIcon: Icon(Icons.space_dashboard_outlined),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _confluenceEmailController,
                        decoration: const InputDecoration(
                          labelText: '계정 이메일',
                          prefixIcon: Icon(Icons.email_outlined),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _confluenceTokenController,
                        obscureText: _obscureConf,
                        decoration: InputDecoration(
                          labelText: 'API Token',
                          prefixIcon: const Icon(Icons.vpn_key_outlined),
                          suffixIcon: IconButton(
                            icon: Icon(_obscureConf ? Icons.visibility_off : Icons.visibility),
                            onPressed: () => setState(() => _obscureConf = !_obscureConf),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (vm.error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(vm.error!, style: const TextStyle(color: Colors.red)),
                ),
              if (vm.success != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(vm.success!, style: const TextStyle(color: Colors.green)),
                ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: vm.isLoading ? null : _saveSettings,
                icon: vm.isLoading
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.save),
                label: const Text('연동 설정 저장'),
              ),
              if (Platform.isAndroid || Platform.isWindows || Platform.isLinux || Platform.isMacOS)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text(
                    '모든 연동은 앱에서 직접 호출되며 별도 서버가 필요하지 않습니다.',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}

class _GeneralSettingsTab extends StatefulWidget {
  const _GeneralSettingsTab();

  @override
  State<_GeneralSettingsTab> createState() => _GeneralSettingsTabState();
}

class _GeneralSettingsTabState extends State<_GeneralSettingsTab> {
  final _nameController = TextEditingController();
  final _passwordController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final vm = context.read<SettingsViewModel>();
      _nameController.text = vm.userName;
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('사용자 설정',
                      style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _nameController,
                    decoration: const InputDecoration(
                      labelText: '담당자명',
                      prefixIcon: Icon(Icons.person_outline),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('관리자 비밀번호 변경',
                      style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: '새 비밀번호',
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: () async {
              final vm = context.read<SettingsViewModel>();
              final updates = <String, String>{
                AppConstants.settingUserName: _nameController.text.trim(),
              };
              if (_passwordController.text.isNotEmpty) {
                updates[AppConstants.settingAdminPassword] =
                    _passwordController.text;
              }
              await vm.saveSettings(updates);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('설정이 저장되었습니다')),
                );
              }
            },
            icon: const Icon(Icons.save),
            label: const Text('설정 저장'),
          ),
          const SizedBox(height: 16),
          const _AppInfo(),
        ],
      ),
    );
  }
}

class _AppInfo extends StatelessWidget {
  const _AppInfo();

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('앱 정보', style: Theme.of(context).textTheme.titleSmall),
            const Divider(height: 16),
            const _InfoItem('앱명', 'AI VOC Assistant'),
            const _InfoItem('버전', '1.0.0'),
            const _InfoItem('데이터베이스', 'SQLite'),
            const _InfoItem('벡터 검색', 'Cosine Similarity (FAISS 대체)'),
            const _InfoItem('지원 플랫폼', 'Android, Windows, Linux, macOS'),
          ],
        ),
      ),
    );
  }
}

class _InfoItem extends StatelessWidget {
  final String label;
  final String value;
  const _InfoItem(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(
            width: 120,
            child: Text(label,
                style: const TextStyle(
                    fontSize: 12, fontWeight: FontWeight.w500)),
          ),
          Expanded(
              child: Text(value, style: const TextStyle(fontSize: 12))),
        ],
      ),
    );
  }
}
