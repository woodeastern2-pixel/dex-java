import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../viewmodels/jira_viewmodel.dart';
import '../../viewmodels/settings_viewmodel.dart';

class JiraScreen extends StatelessWidget {
  const JiraScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('JIRA 연동')),
      body: Consumer<JiraViewModel>(
        builder: (context, vm, _) {
          if (!vm.isConfigured) {
            return _NotConfigured();
          }
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _ConnectionStatus(vm: vm),
                const SizedBox(height: 16),
                _TestConnectionCard(vm: vm),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _NotConfigured extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.link_off, size: 64,
              color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: 16),
          const Text('JIRA가 설정되지 않았습니다',
              style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          const Text('설정 화면에서 JIRA URL, 프로젝트 키, 토큰을 입력해 주세요',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey)),
        ],
      ),
    );
  }
}

class _ConnectionStatus extends StatelessWidget {
  final JiraViewModel vm;
  const _ConnectionStatus({required this.vm});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsViewModel>();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('JIRA 설정', style: Theme.of(context).textTheme.titleSmall),
            const Divider(height: 16),
            _InfoRow('서버 URL', settings.jiraUrl),
            _InfoRow('프로젝트', settings.jiraProjectKey),
            _InfoRow('이메일', settings.jiraEmail),
            Row(
              children: [
                const Icon(Icons.circle, size: 10,
                    color: Colors.green),
                const SizedBox(width: 8),
                Text(vm.isConnected ? '연결됨' : '연결 안 됨',
                    style: TextStyle(
                        color: vm.isConnected ? Colors.green : Colors.grey)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(label,
                style: const TextStyle(
                    fontSize: 12, fontWeight: FontWeight.w500)),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(fontSize: 12)),
          ),
        ],
      ),
    );
  }
}

class _TestConnectionCard extends StatelessWidget {
  final JiraViewModel vm;
  const _TestConnectionCard({required this.vm});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('연결 테스트', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 12),
            if (vm.error != null)
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(vm.error!,
                    style: const TextStyle(color: Colors.red, fontSize: 12)),
              ),
            if (vm.successMessage != null)
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(vm.successMessage!,
                    style: const TextStyle(
                        color: Colors.green, fontSize: 12)),
              ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: vm.isTesting ? null : vm.testConnection,
              icon: vm.isTesting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.wifi_tethering),
              label: Text(vm.isTesting ? '연결 중...' : 'JIRA 연결 테스트'),
            ),
          ],
        ),
      ),
    );
  }
}
