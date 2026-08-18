import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/constants/app_constants.dart';
import '../../../domain/entities/response_entity.dart';
import '../../../domain/entities/voc_entity.dart';
import '../../viewmodels/ai_viewmodel.dart';
import '../../viewmodels/dashboard_viewmodel.dart';
import '../../viewmodels/integration_viewmodel.dart';
import '../../viewmodels/settings_viewmodel.dart';
import '../../viewmodels/voc_viewmodel.dart';
import '../../widgets/priority_chip.dart';
import '../../widgets/voc_status_chip.dart';
import 'voc_detail_screen.dart';
import 'voc_register_screen.dart';

class VocListScreen extends StatefulWidget {
  const VocListScreen({
    super.key,
    this.initialStatus = '',
    this.initialCategory = '',
  });

  final String initialStatus;
  final String initialCategory;

  @override
  State<VocListScreen> createState() => _VocListScreenState();
}

class _VocListScreenState extends State<VocListScreen> {
  static const int _allPageSize = -1;

  String _sortBy = 'latest';
  bool _ascending = false;
  int _pageSize = 25;
  int _currentPage = 1;

  bool _bulkRunning = false;
  bool _bulkStopRequested = false;
  int _bulkTotal = 0;
  int _bulkCurrent = 0;
  int _bulkSuccess = 0;
  int _bulkFailed = 0;
  int _bulkSkipped = 0;
  String _bulkCurrentTitle = '';
  String _bulkLastError = '';
  String _bulkStatusText = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final vm = context.read<VocViewModel>();
      if (widget.initialStatus.isNotEmpty) vm.setFilterStatus(widget.initialStatus);
      if (widget.initialCategory.isNotEmpty) vm.setFilterCategory(widget.initialCategory);
      vm.loadVocs();
    });
  }

  Future<void> _openRegister() async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const VocRegisterScreen()),
    );
    if (!mounted) return;
    await context.read<VocViewModel>().loadVocs();
    await context.read<DashboardViewModel>().loadDashboard();
  }

  void _requestBulkStop() {
    if (!_bulkRunning) return;
    setState(() {
      _bulkStopRequested = true;
      _bulkStatusText = '중지 요청됨 · 현재 요청이 끝나면 종료합니다.';
    });
  }

  Future<void> _runBulkAutoResolve() async {
    if (_bulkRunning) return;

    final vocVm = context.read<VocViewModel>();
    final aiVm = context.read<AiViewModel>();
    final integrationVm = context.read<IntegrationViewModel>();
    final dashboardVm = context.read<DashboardViewModel>();

    await vocVm.loadVocs();
    final targets = vocVm.allVocs
        .where((voc) =>
            voc.status == AppConstants.vocStatusOpen ||
            voc.status == AppConstants.vocStatusInProgress)
        .toList();

    if (targets.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('미처리 VOC가 없습니다.')),
      );
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('미처리 VOC AI 답변 일괄 등록'),
        content: Text(
          '미처리 VOC ${targets.length}건에 대해 AI 답변을 순서대로 생성하고 등록합니다.\n\n'
          '답변 저장이 성공한 항목만 해결 상태로 변경합니다. 근거가 부족하거나 AI 호출에 실패한 항목은 미처리 상태로 남깁니다.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('실행'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _bulkRunning = true;
      _bulkStopRequested = false;
      _bulkTotal = targets.length;
      _bulkCurrent = 0;
      _bulkSuccess = 0;
      _bulkFailed = 0;
      _bulkSkipped = 0;
      _bulkCurrentTitle = '';
      _bulkLastError = '';
      _bulkStatusText = 'AI 답변 일괄 처리를 시작합니다.';
    });

    for (var index = 0; index < targets.length; index++) {
      if (_bulkStopRequested) break;
      final voc = targets[index];
      if (!mounted) break;

      setState(() {
        _bulkCurrent = index + 1;
        _bulkCurrentTitle = voc.title;
        _bulkStatusText = '$_bulkCurrent / $_bulkTotal 번째 답변 처리 중';
        _bulkLastError = '';
      });

      try {
        await vocVm.loadResponsesForVoc(voc.id);
        final responses = List<ResponseEntity>.from(vocVm.responses);
        final approved = responses.where((item) => item.isApproved).toList();

        ResponseEntity? stored;
        if (approved.isNotEmpty) {
          stored = approved.first;
          _bulkSkipped += 1;
        } else {
          final reusableAi = responses.where((item) => item.aiGenerated).toList()
            ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));

          if (reusableAi.isNotEmpty) {
            final existing = reusableAi.first;
            stored = await vocVm.adoptAiAnswer(
              vocId: voc.id,
              content: existing.content,
              confidence: existing.confidenceScore,
              referencedVocIds: existing.referencedVocIds,
              responseId: existing.id,
            );
          } else {
            aiVm.clearResults();
            await aiVm.searchSimilarVocs('${voc.title} ${voc.content}');
            if (_bulkStopRequested) break;

            final result = await aiVm.generateAnswer(voc.title, voc.content);
            if (_bulkStopRequested) break;

            final answer = result?.answer.trim() ?? '';
            final hasGrounding = result != null &&
                answer.isNotEmpty &&
                !(result.confidence <= 0.25 && result.referencedCases.isEmpty);

            if (!hasGrounding) {
              final reason = aiVm.error?.trim().isNotEmpty == true
                  ? aiVm.error!.trim()
                  : (result?.notes.trim().isNotEmpty == true
                      ? result!.notes.trim()
                      : 'AI 답변을 생성하지 못했거나 근거가 부족합니다.');
              throw StateError(reason);
            }

            final referenceIds = aiVm.similarVocs
                .map((item) => item.knowledgeBase.vocId)
                .whereType<String>()
                .toSet()
                .toList();

            stored = await vocVm.adoptAiAnswer(
              vocId: voc.id,
              content: answer,
              confidence: result.confidence,
              referencedVocIds: referenceIds,
            );
          }
        }

        if (stored == null) {
          throw StateError('AI 답변 저장 결과를 확인할 수 없습니다.');
        }

        if (!approved.contains(stored)) {
          try {
            await integrationVm.forwardVocChangeToPeerApps(
              voc: voc,
              event: 'response.approved',
              response: stored,
            );
          } catch (_) {
            // 외부 연동 실패는 로컬 답변 저장 성공을 되돌리지 않는다.
          }
        }

        if (voc.status != AppConstants.vocStatusResolved) {
          await vocVm.updateVocStatus(voc.id, AppConstants.vocStatusResolved);
          final updated = vocVm.allVocs.where((item) => item.id == voc.id);
          if (updated.isNotEmpty) {
            try {
              await integrationVm.forwardVocChangeToPeerApps(
                voc: updated.first,
                event: 'voc.status_changed',
              );
            } catch (_) {
              // 외부 연동 실패는 로컬 해결 처리를 되돌리지 않는다.
            }
          }
        }

        if (!mounted) break;
        setState(() {
          _bulkSuccess += 1;
          _bulkStatusText = '$_bulkCurrent / $_bulkTotal 완료 · 성공 $_bulkSuccess · 실패 $_bulkFailed';
        });
      } catch (e) {
        if (!mounted) break;
        setState(() {
          _bulkFailed += 1;
          _bulkLastError = e.toString().replaceFirst('Bad state: ', '');
          _bulkStatusText = '$_bulkCurrent / $_bulkTotal 처리 실패 · 다음 VOC 계속 진행';
        });
      }
    }

    await vocVm.loadVocs();
    await dashboardVm.loadDashboard();
    if (!mounted) return;

    final stopped = _bulkStopRequested;
    setState(() {
      _bulkRunning = false;
      _bulkStatusText = stopped
          ? '일괄 처리 중지 · 성공 $_bulkSuccess · 실패 $_bulkFailed · 기존답변 $_bulkSkipped'
          : '일괄 처리 완료 · 성공 $_bulkSuccess · 실패 $_bulkFailed · 기존답변 $_bulkSkipped';
      _bulkCurrentTitle = '';
    });

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(_bulkStatusText),
        duration: const Duration(seconds: 5),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final desktop = constraints.maxWidth >= 980;
        return Scaffold(
          appBar: AppBar(
            title: Text(desktop ? 'VOC 관리' : 'VOC 목록'),
            actions: [
              IconButton(
                icon: Icon(
                  _bulkRunning ? Icons.stop_circle_outlined : Icons.auto_awesome_outlined,
                ),
                color: _bulkRunning ? Theme.of(context).colorScheme.error : null,
                tooltip: _bulkRunning ? '일괄 처리 중지' : '미처리 VOC AI 답변 일괄 등록',
                onPressed: _bulkRunning ? _requestBulkStop : _runBulkAutoResolve,
              ),
              IconButton(
                icon: const Icon(Icons.refresh),
                tooltip: '새로고침',
                onPressed: _bulkRunning
                    ? null
                    : () => context.read<VocViewModel>().loadVocs(),
              ),
            ],
          ),
          floatingActionButton: desktop
              ? null
              : FloatingActionButton.extended(
                  onPressed: _bulkRunning ? null : _openRegister,
                  icon: const Icon(Icons.add),
                  label: const Text('VOC 등록'),
                ),
          bottomNavigationBar: _buildBulkStatusBar(context),
          body: Consumer<VocViewModel>(
            builder: (context, vm, _) {
              final sorted = _sortVocs(vm.vocs);
              final pages = _pageCount(sorted.length);
              final page = _currentPage.clamp(1, pages);
              final visible = _paged(sorted, page);
              if (page != _currentPage) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted) setState(() => _currentPage = page);
                });
              }

              return Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1540),
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(desktop ? 20 : 12, 14, desktop ? 20 : 12, 0),
                    child: Column(
                      children: [
                        _Toolbar(
                          vm: vm,
                          desktop: desktop,
                          sortBy: _sortBy,
                          ascending: _ascending,
                          pageSize: _pageSize,
                          page: page,
                          pages: pages,
                          total: sorted.length,
                          onSortChanged: (value) => setState(() {
                            _sortBy = value;
                            _currentPage = 1;
                          }),
                          onAscendingChanged: () => setState(() => _ascending = !_ascending),
                          onPageSizeChanged: (value) => setState(() {
                            _pageSize = value;
                            _currentPage = 1;
                          }),
                          onPrevious: page > 1 ? () => setState(() => _currentPage--) : null,
                          onNext: page < pages ? () => setState(() => _currentPage++) : null,
                          onRegister: _bulkRunning ? null : _openRegister,
                        ),
                        const SizedBox(height: 12),
                        Expanded(
                          child: vm.isLoading && !_bulkRunning
                              ? const Center(child: CircularProgressIndicator())
                              : visible.isEmpty
                                  ? const Center(child: Text('조건에 맞는 VOC가 없습니다.'))
                                  : desktop
                                      ? _DesktopVocList(vocs: visible)
                                      : ListView.builder(
                                          padding: const EdgeInsets.only(bottom: 90),
                                          itemCount: visible.length,
                                          itemBuilder: (_, index) => _VocCard(voc: visible[index]),
                                        ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget? _buildBulkStatusBar(BuildContext context) {
    if (!_bulkRunning && _bulkStatusText.isEmpty) return null;
    final cs = Theme.of(context).colorScheme;
    final progress = _bulkTotal == 0 ? 0.0 : (_bulkCurrent / _bulkTotal).clamp(0.0, 1.0);
    return SafeArea(
      top: false,
      child: Material(
        elevation: 10,
        color: cs.surface,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_bulkRunning) LinearProgressIndicator(value: progress),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
              child: Row(
                children: [
                  Icon(
                    _bulkRunning ? Icons.auto_awesome : Icons.task_alt,
                    size: 20,
                    color: _bulkRunning ? cs.primary : cs.secondary,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _bulkStatusText,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        if (_bulkRunning && _bulkCurrentTitle.isNotEmpty)
                          Text(
                            '현재: $_bulkCurrentTitle',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        if (_bulkLastError.isNotEmpty)
                          Text(
                            '최근 오류: $_bulkLastError',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: cs.error),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    '$_bulkCurrent / $_bulkTotal',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  if (_bulkRunning) ...[
                    const SizedBox(width: 8),
                    TextButton.icon(
                      onPressed: _bulkStopRequested ? null : _requestBulkStop,
                      icon: const Icon(Icons.stop_circle_outlined, size: 18),
                      label: const Text('중지'),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  int _pageCount(int total) {
    if (total == 0 || _pageSize == _allPageSize) return 1;
    return (total / _pageSize).ceil();
  }

  List<VocEntity> _paged(List<VocEntity> list, int page) {
    if (_pageSize == _allPageSize) return list;
    final start = (page - 1) * _pageSize;
    if (start >= list.length) return const [];
    final end = (start + _pageSize).clamp(0, list.length);
    return list.sublist(start, end);
  }

  List<VocEntity> _sortVocs(List<VocEntity> input) {
    final list = [...input];
    int compare(VocEntity a, VocEntity b) {
      switch (_sortBy) {
        case 'title':
          return a.title.toLowerCase().compareTo(b.title.toLowerCase());
        case 'customer':
          return a.customer.toLowerCase().compareTo(b.customer.toLowerCase());
        case 'status':
          return a.status.compareTo(b.status);
        case 'priority':
          return _priorityRank(a.priority).compareTo(_priorityRank(b.priority));
        case 'updated':
          return a.updatedAt.compareTo(b.updatedAt);
        default:
          return a.createdAt.compareTo(b.createdAt);
      }
    }
    list.sort(compare);
    return _ascending ? list : list.reversed.toList();
  }

  int _priorityRank(String priority) {
    switch (priority.toUpperCase()) {
      case AppConstants.priorityHigh:
        return 0;
      case AppConstants.priorityMedium:
        return 1;
      case AppConstants.priorityLow:
        return 2;
      default:
        return 3;
    }
  }
}

class _Toolbar extends StatelessWidget {
  const _Toolbar({
    required this.vm,
    required this.desktop,
    required this.sortBy,
    required this.ascending,
    required this.pageSize,
    required this.page,
    required this.pages,
    required this.total,
    required this.onSortChanged,
    required this.onAscendingChanged,
    required this.onPageSizeChanged,
    required this.onPrevious,
    required this.onNext,
    required this.onRegister,
  });

  final VocViewModel vm;
  final bool desktop;
  final String sortBy;
  final bool ascending;
  final int pageSize;
  final int page;
  final int pages;
  final int total;
  final ValueChanged<String> onSortChanged;
  final VoidCallback onAscendingChanged;
  final ValueChanged<int> onPageSizeChanged;
  final VoidCallback? onPrevious;
  final VoidCallback? onNext;
  final VoidCallback? onRegister;

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsViewModel>().allCategories;
    final category = categories.contains(vm.filterCategory) ? vm.filterCategory : '';
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Wrap(
          spacing: 10,
          runSpacing: 10,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            SizedBox(
              width: desktop ? 300 : double.infinity,
              child: TextField(
                onChanged: vm.setSearch,
                decoration: const InputDecoration(
                  hintText: 'VOC 검색',
                  prefixIcon: Icon(Icons.search),
                ),
              ),
            ),
            SizedBox(
              width: 170,
              child: DropdownButtonFormField<String>(
                initialValue: vm.filterStatus,
                decoration: const InputDecoration(labelText: '상태'),
                items: const [
                  DropdownMenuItem(value: '', child: Text('전체')),
                  DropdownMenuItem(value: AppConstants.vocStatusOpen, child: Text('미처리')),
                  DropdownMenuItem(value: AppConstants.vocStatusInProgress, child: Text('처리중')),
                  DropdownMenuItem(value: AppConstants.vocStatusResolved, child: Text('해결')),
                  DropdownMenuItem(value: AppConstants.vocStatusRejected, child: Text('반려')),
                ],
                onChanged: (value) => vm.setFilterStatus(value ?? ''),
              ),
            ),
            SizedBox(
              width: 190,
              child: DropdownButtonFormField<String>(
                initialValue: category,
                decoration: const InputDecoration(labelText: '카테고리'),
                items: [
                  const DropdownMenuItem(value: '', child: Text('전체')),
                  ...categories.map((item) => DropdownMenuItem(value: item, child: Text(item))),
                ],
                onChanged: (value) => vm.setFilterCategory(value ?? ''),
              ),
            ),
            SizedBox(
              width: 160,
              child: DropdownButtonFormField<String>(
                initialValue: sortBy,
                decoration: const InputDecoration(labelText: '정렬'),
                items: const [
                  DropdownMenuItem(value: 'latest', child: Text('등록일')),
                  DropdownMenuItem(value: 'updated', child: Text('수정일')),
                  DropdownMenuItem(value: 'title', child: Text('제목')),
                  DropdownMenuItem(value: 'customer', child: Text('고객')),
                  DropdownMenuItem(value: 'status', child: Text('상태')),
                  DropdownMenuItem(value: 'priority', child: Text('우선순위')),
                ],
                onChanged: (value) {
                  if (value != null) onSortChanged(value);
                },
              ),
            ),
            IconButton.filledTonal(
              onPressed: onAscendingChanged,
              icon: Icon(ascending ? Icons.arrow_upward : Icons.arrow_downward),
              tooltip: ascending ? '오름차순' : '내림차순',
            ),
            SizedBox(
              width: 120,
              child: DropdownButtonFormField<int>(
                initialValue: pageSize,
                decoration: const InputDecoration(labelText: '표시 수'),
                items: const [
                  DropdownMenuItem(value: 25, child: Text('25개')),
                  DropdownMenuItem(value: 50, child: Text('50개')),
                  DropdownMenuItem(value: 100, child: Text('100개')),
                  DropdownMenuItem(value: -1, child: Text('전체')),
                ],
                onChanged: (value) {
                  if (value != null) onPageSizeChanged(value);
                },
              ),
            ),
            Text('총 $total건 · $page / $pages'),
            IconButton.filledTonal(onPressed: onPrevious, icon: const Icon(Icons.chevron_left)),
            IconButton.filledTonal(onPressed: onNext, icon: const Icon(Icons.chevron_right)),
            if (desktop)
              FilledButton.icon(
                onPressed: onRegister,
                icon: const Icon(Icons.add),
                label: const Text('VOC 등록'),
              ),
          ],
        ),
      ),
    );
  }
}

class _DesktopVocList extends StatelessWidget {
  const _DesktopVocList({required this.vocs});
  final List<VocEntity> vocs;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: ListView.separated(
        itemCount: vocs.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final voc = vocs[index];
          return ListTile(
            onTap: () => _openDetail(context, voc),
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            leading: SizedBox(width: 92, child: VocStatusChip(status: voc.status)),
            title: Text(voc.title, maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text(
              '${voc.customer} · ${voc.project} · ${voc.category}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: SizedBox(width: 90, child: PriorityChip(priority: voc.priority)),
          );
        },
      ),
    );
  }
}

class _VocCard extends StatelessWidget {
  const _VocCard({required this.voc});
  final VocEntity voc;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 9),
      child: InkWell(
        onTap: () => _openDetail(context, voc),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  VocStatusChip(status: voc.status),
                  PriorityChip(priority: voc.priority),
                  Chip(label: Text(voc.category), visualDensity: VisualDensity.compact),
                ],
              ),
              const SizedBox(height: 10),
              Text(voc.title, style: const TextStyle(fontWeight: FontWeight.w800)),
              const SizedBox(height: 5),
              Text(voc.content, maxLines: 2, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 8),
              Text('${voc.customer} · ${voc.project}', style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
        ),
      ),
    );
  }
}

Future<void> _openDetail(BuildContext context, VocEntity voc) async {
  await Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => VocDetailScreen(vocId: voc.id)),
  );
  if (!context.mounted) return;
  await context.read<VocViewModel>().loadVocs();
  await context.read<DashboardViewModel>().loadDashboard();
}
