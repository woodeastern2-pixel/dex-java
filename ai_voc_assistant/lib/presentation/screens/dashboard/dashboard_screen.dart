import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:fl_chart/fl_chart.dart';
import '../../../data/services/demo_mode_service.dart';
import '../../../data/services/sample_voc_generator.dart';
import '../../viewmodels/dashboard_viewmodel.dart';
import '../../viewmodels/voc_viewmodel.dart';
import '../voc/voc_register_screen.dart';
import '../voc/voc_list_screen.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('대시보드'),
        actions: [
          IconButton(
            icon: const Icon(Icons.play_circle_fill_outlined),
            tooltip: 'Demo Mode',
            onPressed: () => _showDemoModeDialog(context),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<DashboardViewModel>().loadDashboard(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const VocRegisterScreen()),
        ).then((_) {
          context.read<DashboardViewModel>().loadDashboard();
          context.read<VocViewModel>().loadVocs();
        }),
        icon: const Icon(Icons.add),
        label: const Text('VOC 등록'),
      ),
      body: Consumer<DashboardViewModel>(
        builder: (context, vm, _) {
          if (vm.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          return RefreshIndicator(
            onRefresh: vm.loadDashboard,
            child: SingleChildScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 요약 카드들
                  _SummaryCards(vm: vm),
                  const SizedBox(height: 16),
                  _ExecutiveInsightsPanel(vm: vm),
                  const SizedBox(height: 24),
                  // 카테고리별 분포
                  if (vm.vocByCategory.isNotEmpty) ...[
                    Text('카테고리별 VOC',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 12),
                    _CategoryChart(data: vm.vocByCategory),
                    const SizedBox(height: 24),
                  ],
                  // 월별 추이
                  if (vm.monthlyStats.isNotEmpty) ...[
                    Text('월별 VOC 추이',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 12),
                    _MonthlyChart(stats: vm.monthlyStats),
                    const SizedBox(height: 24),
                  ],
                  if (vm.assigneeStats.isNotEmpty) ...[
                    Text('담당자별 처리 현황',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 12),
                    _AssigneeChart(stats: vm.assigneeStats),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _showDemoModeDialog(BuildContext context) async {
    final service = DefaultDemoModeService();
    final logs = <String>[];

    await showDialog<void>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setState) {
            return AlertDialog(
              title: const Text('Demo Mode (3분 모의 시연)'),
              content: SizedBox(
                width: 520,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LinearProgressIndicator(
                      value: (service.getCurrentStatus()?.progressPercent ?? 0) / 100,
                    ),
                    const SizedBox(height: 12),
                    Text(service.getCurrentStatus()?.message ?? '시연 준비 중...'),
                    const SizedBox(height: 12),
                    SizedBox(
                      height: 220,
                      child: ListView.builder(
                        itemCount: logs.length,
                        itemBuilder: (_, i) => Text(
                          logs[i],
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () async {
                    await service.stopDemo();
                    if (ctx.mounted) Navigator.of(ctx).pop();
                  },
                  child: const Text('닫기'),
                ),
                FilledButton.icon(
                  onPressed: service.isRunning()
                      ? null
                      : () async {
                          // 샘플 데이터 임포트
                          final samples = SampleVocGenerator.generateSampleVocs();
                          final count = await context.read<VocViewModel>().importSampleVocs(samples);
                          logs.add('✓ 샘플 데이터 $count개 생성됨');
                          
                          await service.startDemo((status) {
                            logs
                              ..clear()
                              ..addAll(status.logs);
                            if (ctx.mounted) setState(() {});
                          });
                          if (ctx.mounted) {
                            context.read<DashboardViewModel>().loadDashboard();
                            context.read<VocViewModel>().loadVocs();
                          }
                        },
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('시연 시작'),
                ),
              ],
            );
          },
        );
      },
    );
  }
}

class _ExecutiveInsightsPanel extends StatelessWidget {
  final DashboardViewModel vm;
  const _ExecutiveInsightsPanel({required this.vm});

  @override
  Widget build(BuildContext context) {
    final roi = vm.roiResult;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('경영진 인사이트', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                _metricChip(
                  context,
                  'AI 정확도',
                  '${(vm.aiOverallAccuracy * 100).toStringAsFixed(1)}%',
                  Icons.verified_outlined,
                  Colors.teal,
                ),
                _metricChip(
                  context,
                  '답변 채택률',
                  '${(vm.aiAnswerAdoptionRate * 100).toStringAsFixed(1)}%',
                  Icons.thumb_up_alt_outlined,
                  Colors.indigo,
                ),
                _metricChip(
                  context,
                  '월간 절감액',
                  roi == null ? '-' : '\$${roi.monthlySavingsCost.toStringAsFixed(0)}',
                  Icons.savings_outlined,
                  Colors.green,
                ),
                _metricChip(
                  context,
                  'ROI',
                  roi == null ? '-' : '${roi.roi.toStringAsFixed(1)}%',
                  Icons.trending_up,
                  Colors.deepPurple,
                ),
              ],
            ),
            if (vm.accuracyRecommendations.isNotEmpty) ...[
              const SizedBox(height: 14),
              Text('AI 개선 권장사항',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 6),
              ...vm.accuracyRecommendations.take(2).map(
                (r) => Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    r,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _metricChip(
    BuildContext context,
    String label,
    String value,
    IconData icon,
    Color color,
  ) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withOpacity(0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 6),
          Text(
            '$label: $value',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: color,
                  fontWeight: FontWeight.w600,
                ),
          ),
        ],
      ),
    );
  }
}

class _SummaryCards extends StatelessWidget {
  final DashboardViewModel vm;
  const _SummaryCards({required this.vm});

  @override
  Widget build(BuildContext context) {
    final cards = [
      _CardData('전체 VOC', vm.totalVocs.toString(), Icons.inbox, Colors.blue, ''),
      _CardData('미처리', vm.openVocs.toString(), Icons.fiber_new, Colors.orange, 'OPEN'),
      _CardData('처리중', vm.inProgressVocs.toString(), Icons.pending, Colors.purple, 'IN_PROGRESS'),
      _CardData('해결', vm.resolvedVocs.toString(), Icons.check_circle, Colors.green, 'RESOLVED'),
      _CardData(
        '해결률',
        '${(vm.resolutionRate * 100).toStringAsFixed(1)}%',
        Icons.percent,
        Colors.teal,
        '',
      ),
      _CardData(
        '지식베이스',
        vm.kbCount.toString(),
        Icons.book,
        Colors.indigo,
        '',
      ),
      _CardData(
        '중복 감소율',
        '${(vm.duplicateReductionRate * 100).toStringAsFixed(1)}%',
        Icons.content_copy,
        Colors.cyan,
        '',
      ),
      _CardData(
        'AI 활용률',
        '${(vm.aiUsageRate * 100).toStringAsFixed(1)}%',
        Icons.auto_awesome,
        Colors.deepPurple,
        '',
      ),
      _CardData(
        '평균 처리시간',
        '${(vm.avgProcessMinutes / 60).toStringAsFixed(1)}h',
        Icons.schedule,
        Colors.brown,
        '',
      ),
    ];

    return LayoutBuilder(builder: (context, constraints) {
      final crossCount = constraints.maxWidth > 600 ? 3 : 2;
      return GridView.builder(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: crossCount,
          childAspectRatio: 1.6,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
        ),
        itemCount: cards.length,
        itemBuilder: (_, i) => _SummaryCard(data: cards[i], vm: vm),
      );
    });
  }
}

class _CardData {
  final String label;
  final String value;
  final IconData icon;
  final Color color;
  final String statusFilter;
  const _CardData(this.label, this.value, this.icon, this.color, this.statusFilter);
}

class _SummaryCard extends StatelessWidget {
  final _CardData data;
  final DashboardViewModel vm;
  const _SummaryCard({required this.data, required this.vm});

  void _navigateToFilteredList(BuildContext context) {
    if (data.statusFilter.isEmpty) return;
    
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => VocListScreen(initialStatus: data.statusFilter),
      ),
    ).then((_) {
      vm.loadDashboard();
      context.read<VocViewModel>().loadVocs();
    });
  }

  @override
  Widget build(BuildContext context) {
    final isClickable = data.statusFilter.isNotEmpty;
    
    return InkWell(
      onTap: isClickable ? () => _navigateToFilteredList(context) : null,
      borderRadius: BorderRadius.circular(12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(data.icon, color: data.color, size: 20),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      data.label,
                      style: Theme.of(context).textTheme.bodySmall,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              Text(
                data.value,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: data.color,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CategoryChart extends StatelessWidget {
  final Map<String, int> data;
  const _CategoryChart({required this.data});

  @override
  Widget build(BuildContext context) {
    final total = data.values.fold(0, (a, b) => a + b);
    if (total == 0) return const SizedBox.shrink();

    final colors = [
      Colors.blue, Colors.orange, Colors.green,
      Colors.red, Colors.purple, Colors.teal, Colors.amber,
    ];

    final sections = data.entries.toList().asMap().entries.map((entry) {
      final i = entry.key;
      final e = entry.value;
      final pct = e.value / total * 100;
      return PieChartSectionData(
        color: colors[i % colors.length],
        value: e.value.toDouble(),
        title: '${pct.toStringAsFixed(0)}%',
        radius: 60,
        titleStyle: const TextStyle(fontSize: 11, color: Colors.white, fontWeight: FontWeight.bold),
      );
    }).toList();

    return SizedBox(
      height: 200,
      child: Row(
        children: [
          Expanded(
            child: PieChart(PieChartData(sections: sections, sectionsSpace: 2)),
          ),
          const SizedBox(width: 16),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: data.entries.toList().asMap().entries.map((entry) {
              final i = entry.key;
              final e = entry.value;
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 12,
                      height: 12,
                      color: colors[i % colors.length],
                    ),
                    const SizedBox(width: 6),
                    Text('${e.key} (${e.value})',
                        style: const TextStyle(fontSize: 12)),
                  ],
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class _MonthlyChart extends StatelessWidget {
  final List<Map<String, dynamic>> stats;
  const _MonthlyChart({required this.stats});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final maxY = stats.fold<double>(
      0,
      (m, s) => (s['total'] as int) > m ? (s['total'] as int).toDouble() : m,
    );

    return SizedBox(
      height: 200,
      child: BarChart(
        BarChartData(
          maxY: maxY + 2,
          barGroups: stats.asMap().entries.map((entry) {
            final i = entry.key;
            final s = entry.value;
            final total = (s['total'] as int).toDouble();
            final resolved = (s['resolved'] as int).toDouble();
            return BarChartGroupData(
              x: i,
              barRods: [
                BarChartRodData(
                  toY: total,
                  color: colorScheme.primary.withOpacity(0.6),
                  width: 14,
                ),
                BarChartRodData(
                  toY: resolved,
                  color: colorScheme.secondary,
                  width: 14,
                ),
              ],
            );
          }).toList(),
          titlesData: FlTitlesData(
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (v, meta) {
                  final idx = v.toInt();
                  if (idx < 0 || idx >= stats.length) return const SizedBox();
                  final month = stats[idx]['month'] as String;
                  return Text(
                    month.substring(5),
                    style: const TextStyle(fontSize: 10),
                  );
                },
              ),
            ),
            leftTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 28,
                getTitlesWidget: (v, _) => Text(
                  v.toInt().toString(),
                  style: const TextStyle(fontSize: 10),
                ),
              ),
            ),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          ),
          borderData: FlBorderData(show: false),
          gridData: const FlGridData(show: true),
        ),
      ),
    );
  }
}

class _AssigneeChart extends StatelessWidget {
  final List<Map<String, dynamic>> stats;
  const _AssigneeChart({required this.stats});

  @override
  Widget build(BuildContext context) {
    final maxY = stats.fold<double>(
      0,
      (m, s) => ((s['handled'] as int?) ?? 0) > m
          ? ((s['handled'] as int).toDouble())
          : m,
    );

    return SizedBox(
      height: 220,
      child: BarChart(
        BarChartData(
          maxY: maxY + 1,
          barGroups: stats.asMap().entries.map((e) {
            final i = e.key;
            final s = e.value;
            return BarChartGroupData(
              x: i,
              barRods: [
                BarChartRodData(
                  toY: ((s['handled'] as int?) ?? 0).toDouble(),
                  color: Colors.teal,
                  width: 20,
                ),
              ],
            );
          }).toList(),
          titlesData: FlTitlesData(
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (v, _) {
                  final i = v.toInt();
                  if (i < 0 || i >= stats.length) return const SizedBox();
                  final name = stats[i]['assignee'] as String? ?? '-';
                  return Text(
                    name,
                    style: const TextStyle(fontSize: 10),
                    overflow: TextOverflow.ellipsis,
                  );
                },
              ),
            ),
            leftTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: true, reservedSize: 28),
            ),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          ),
        ),
      ),
    );
  }
}
