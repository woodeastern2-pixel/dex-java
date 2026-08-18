import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_theme.dart';
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
  static const int _pageSizeAll = -1;
  String _sortBy = 'latest';
  bool _ascending = false;
  int _pageSize = 25;
  int _currentPage = 1;
  bool _mobileFiltersExpanded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final vm = context.read<VocViewModel>();
      if (widget.initialStatus.isNotEmpty) {
        vm.setFilterStatus(widget.initialStatus);
      }
      if (widget.initialCategory.isNotEmpty) {
        vm.setFilterCategory(widget.initialCategory);
      }
      vm.loadVocs();
    });
  }

  Future<void> _openRegister() async {
    final vocVm = context.read<VocViewModel>();
    final dashboardVm = context.read<DashboardViewModel>();
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const VocRegisterScreen()),
    );
    if (!mounted) return;
    vocVm.loadVocs();
    dashboardVm.loadDashboard();
  }

  @override
  Widget build(BuildContext context) {
    final bulkRunning = context.watch<VocViewModel>().isBulkAutoResolving;
    return LayoutBuilder(
      builder: (context, outer) {
        final desktop = outer.maxWidth >= 980;
        return Scaffold(
          appBar: AppBar(
            title: Text(desktop ? 'VOC 관리' : 'VOC 목록'),
            actions: [
              IconButton(
                icon: bulkRunning
                    ? const Icon(Icons.stop_circle_outlined)
                    : const Icon(Icons.auto_awesome_outlined),
                color: bulkRunning ? Theme.of(context).colorScheme.error : null,
                tooltip: bulkRunning
                    ? '현재 일괄 처리 중지'
                    : '미처리 VOC AI 자동 처리',
                onPressed: bulkRunning
                    ? () {
                        context.read<VocViewModel>().stopBulkAutoResolve();
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('중지 요청됨: 현재 AI 요청이 끝나면 종료합니다.'),
                          ),
                        );
                      }
                    : _runBulkAutoResolve,
              ),
              IconButton(
                icon: const Icon(Icons.refresh),
                tooltip: '새로고침',
                onPressed: () => context.read<VocViewModel>().loadVocs(),
              ),
            ],
          ),
          floatingActionButton: desktop
              ? null
              : FloatingActionButton.extended(
                  onPressed: _openRegister,
                  icon: const Icon(Icons.add),
                  label: const Text('VOC 등록'),
                ),
          body: Consumer<VocViewModel>(
            builder: (context, vm, _) {
              final sorted = _sortVocList(vm.vocs);
              final totalPages = _calculateTotalPages(sorted.length);
              final currentPage = _normalizePage(totalPages);
              final paged = _paginate(sorted, currentPage);
              if (currentPage != _currentPage) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted) setState(() => _currentPage = currentPage);
                });
              }

              return Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1540),
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      desktop ? 20 : 12,
                      desktop ? 18 : 10,
                      desktop ? 20 : 12,
                      0,
                    ),
                    child: Column(
                      children: [
                        if (desktop)
                          _DesktopToolbar(
                            vm: vm,
                            sortBy: _sortBy,
                            ascending: _ascending,
                            totalCount: sorted.length,
                            currentPage: currentPage,
                            totalPages: totalPages,
                            pageSize: _pageSize,
                            onSortChanged: (value) => setState(() {
                              _sortBy = value;
                              _currentPage = 1;
                            }),
                            onDirectionChanged: (value) =>
                                setState(() => _ascending = value),
                            onPageSizeChanged: (value) => setState(() {
                              _pageSize = value;
                              _currentPage = 1;
                            }),
                            onPreviousPage: currentPage > 1
                                ? () => setState(() => _currentPage -= 1)
                                : null,
                            onNextPage: currentPage < totalPages
                                ? () => setState(() => _currentPage += 1)
                                : null,
                            onRegister: _openRegister,
                          )
                        else
                          _MobileToolbar(
                            vm: vm,
                            sortBy: _sortBy,
                            ascending: _ascending,
                            expanded: _mobileFiltersExpanded,
                            totalCount: sorted.length,
                            currentPage: currentPage,
                            totalPages: totalPages,
                            pageSize: _pageSize,
                            onToggleExpanded: () => setState(
                              () => _mobileFiltersExpanded = !_mobileFiltersExpanded,
                            ),
                            onSortChanged: (value) => setState(() {
                              _sortBy = value;
                              _currentPage = 1;
                            }),
                            onDirectionChanged: (value) =>
                                setState(() => _ascending = value),
                            onPageSizeChanged: (value) => setState(() {
                              _pageSize = value;
                              _currentPage = 1;
                            }),
                            onPreviousPage: currentPage > 1
                                ? () => setState(() => _currentPage -= 1)
                                : null,
                            onNextPage: currentPage < totalPages
                                ? () => setState(() => _currentPage += 1)
                                : null,
                          ),
                        const SizedBox(height: 12),
                        Expanded(
                          child: vm.isLoading
                              ? const Center(child: CircularProgressIndicator())
                              : paged.isEmpty
                                  ? _EmptyState(onRegister: _openRegister)
                                  : RefreshIndicator(
                                      onRefresh: vm.loadVocs,
                                      child: desktop
                                          ? _VocDesktopTable(vocs: paged)
                                          : ListView.builder(
                                              padding: const EdgeInsets.only(bottom: 90),
                                              itemCount: paged.length,
                                              itemBuilder: (_, i) =>
                                                  _VocCard(voc: paged[i]),
                                            ),
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

  int _calculateTotalPages(int totalCount) {
    if (totalCount == 0 || _pageSize == _pageSizeAll) return 1;
    return (totalCount / _pageSize).ceil();
  }

  int _normalizePage(int totalPages) {
    if (_currentPage < 1) return 1;
    if (_currentPage > totalPages) return totalPages;
    return _currentPage;
  }

  List<VocEntity> _paginate(List<VocEntity> list, int currentPage) {
    if (_pageSize == _pageSizeAll) return list;
    final start = (currentPage - 1) * _pageSize;
    if (start >= list.length) return const [];
    return list.sublist(start, (start + _pageSize).clamp(0, list.length));
  }

  List<VocEntity> _sortVocList(List<VocEntity> input) {
    final list = [...input];
    int compare(VocEntity a, VocEntity b) {
      switch (_sortBy) {
        case 'title':
          return a.title.toLowerCase().compareTo(b.title.toLowerCase());
        case 'customer':
          return a.customer.toLowerCase().compareTo(b.customer.toLowerCase());
        case 'vocNumber':
          return _compareVocNumber(a, b);
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

  int _compareVocNumber(VocEntity a, VocEntity b) {
    final an = _vocNumberNumericValue(a);
    final bn = _vocNumberNumericValue(b);
    if (an != null && bn != null) {
      final diff = an.compareTo(bn);
      if (diff != 0) return diff;
    } else if (an != null) {
      return -1;
    } else if (bn != null) {
      return 1;
    }
    return _vocNumberTokenForSort(a).compareTo(_vocNumberTokenForSort(b));
  }

  int _priorityRank(String priority) {
    switch (priority.toUpperCase()) {
      case 'HIGH':
        return 0;
      case 'MEDIUM':
        return 1;
      case 'LOW':
        return 2;
      default:
        return 3;
    }
  }

  int? _vocNumberNumericValue(VocEntity voc) {
    final match = RegExp(r'(\d+)').firstMatch(_vocNumberTokenForSort(voc));
    return match == null ? null : int.tryParse(match.group(1)!);
  }

  String _vocNumberTokenForSort(VocEntity voc) {
    final parts = voc.project
        .split('|')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    return parts.isEmpty ? '' : parts.last.toUpperCase();
  }

  Future<void> _runBulkAutoResolve() async {
    final vocVm = context.read<VocViewModel>();
    final aiVm = context.read<AiViewModel>();
    final integrationVm = context.read<IntegrationViewModel>();
    final dashboardVm = context.read<DashboardViewModel>();
    final messenger = ScaffoldMessenger.of(context);
    final targetCount = vocVm.pendingVocCount;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('AI 자동 처리'),
        content: Text(
          '현재 미처리 VOC $targetCount건을 순서대로 처리합니다.\n\n'
          'AI 답변을 생성하거나 기존 답변을 재사용하고, 성공한 항목을 해결 상태로 변경합니다. 진행 중에도 중지할 수 있습니다.',
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

    final result = await vocVm.autoResolvePendingWithAi(
      prepareSimilarCases: (query) => aiVm.searchSimilarVocs(query),
      generateAnswer: aiVm.generateAnswer,
      onResponseApproved: (voc, response) => integrationVm.forwardVocChangeToPeerApps(
        voc: voc,
        event: 'response.approved',
        response: response,
      ),
      onStatusChanged: (voc) => integrationVm.forwardVocChangeToPeerApps(
        voc: voc,
        event: 'voc.status_changed',
      ),
    );
    if (!mounted) return;
    await dashboardVm.loadDashboard();
    messenger.showSnackBar(SnackBar(
      content: Text(
        '${result.stopped ? 'AI 처리 중지' : 'AI 처리 완료'} · '
        '대상 ${result.targetCount}건 · 해결 ${result.resolvedCount}건 · 실패 ${result.failedCount}건',
      ),
    ));
  }
}

class _DesktopToolbar extends StatelessWidget {
  const _DesktopToolbar({
    required this.vm,
    required this.sortBy,
    required this.ascending,
    required this.totalCount,
    required this.currentPage,
    required this.totalPages,
    required this.pageSize,
    required this.onSortChanged,
    required this.onDirectionChanged,
    required this.onPageSizeChanged,
    required this.onPreviousPage,
    required this.onNextPage,
    required this.onRegister,
  });

  final VocViewModel vm;
  final String sortBy;
  final bool ascending;
  final int totalCount;
  final int currentPage;
  final int totalPages;
  final int pageSize;
  final ValueChanged<String> onSortChanged;
  final ValueChanged<bool> onDirectionChanged;
  final ValueChanged<int> onPageSizeChanged;
  final VoidCallback? onPreviousPage;
  final VoidCallback? onNextPage;
  final VoidCallback onRegister;

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsViewModel>().allCategories;
    final selectedCategory = categories.contains(vm.filterCategory) ? vm.filterCategory : '';
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: _SearchField(onChanged: vm.setSearch),
                ),
                const SizedBox(width: 10),
                SizedBox(
                  width: 180,
                  child: _SortDropdown(value: sortBy, onChanged: onSortChanged),
                ),
                const SizedBox(width: 8),
                IconButton.filledTonal(
                  onPressed: () => onDirectionChanged(!ascending),
                  icon: Icon(ascending ? Icons.arrow_upward : Icons.arrow_downward),
                  tooltip: ascending ? '오름차순' : '내림차순',
                ),
                const SizedBox(width: 10),
                SizedBox(
                  width: 220,
                  child: DropdownButtonFormField<String>(
                    value: selectedCategory,
                    isDense: true,
                    decoration: const InputDecoration(
                      labelText: '카테고리',
                      prefixIcon: Icon(Icons.category_outlined),
                    ),
                    items: [
                      const DropdownMenuItem(value: '', child: Text('전체 카테고리')),
                      ...categories.map((e) => DropdownMenuItem(value: e, child: Text(e))),
                    ],
                    onChanged: (v) => vm.setFilterCategory(v ?? ''),
                  ),
                ),
                const SizedBox(width: 10),
                FilledButton.icon(
                  onPressed: onRegister,
                  icon: const Icon(Icons.add),
                  label: const Text('VOC 등록'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: _StatusFilters(vm: vm)),
                const SizedBox(width: 16),
                Text(
                  '총 $totalCount건 · $currentPage / $totalPages 페이지',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(width: 12),
                SizedBox(
                  width: 118,
                  child: _PageSizeDropdown(value: pageSize, onChanged: onPageSizeChanged),
                ),
                const SizedBox(width: 8),
                IconButton.filledTonal(
                  onPressed: onPreviousPage,
                  icon: const Icon(Icons.chevron_left),
                  tooltip: '이전 페이지',
                ),
                const SizedBox(width: 4),
                IconButton.filledTonal(
                  onPressed: onNextPage,
                  icon: const Icon(Icons.chevron_right),
                  tooltip: '다음 페이지',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _MobileToolbar extends StatelessWidget {
  const _MobileToolbar({
    required this.vm,
    required this.sortBy,
    required this.ascending,
    required this.expanded,
    required this.totalCount,
    required this.currentPage,
    required this.totalPages,
    required this.pageSize,
    required this.onToggleExpanded,
    required this.onSortChanged,
    required this.onDirectionChanged,
    required this.onPageSizeChanged,
    required this.onPreviousPage,
    required this.onNextPage,
  });

  final VocViewModel vm;
  final String sortBy;
  final bool ascending;
  final bool expanded;
  final int totalCount;
  final int currentPage;
  final int totalPages;
  final int pageSize;
  final VoidCallback onToggleExpanded;
  final ValueChanged<String> onSortChanged;
  final ValueChanged<bool> onDirectionChanged;
  final ValueChanged<int> onPageSizeChanged;
  final VoidCallback? onPreviousPage;
  final VoidCallback? onNextPage;

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsViewModel>().allCategories;
    final selectedCategory = categories.contains(vm.filterCategory) ? vm.filterCategory : '';
    return Column(
      children: [
        _SearchField(onChanged: vm.setSearch),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: _StatusFilters(vm: vm),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            TextButton.icon(
              onPressed: onToggleExpanded,
              icon: Icon(expanded ? Icons.tune : Icons.tune_outlined),
              label: Text(expanded ? '상세 필터 닫기' : '상세 필터'),
            ),
            const Spacer(),
            Text('총 $totalCount건', style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
        if (expanded) ...[
          const SizedBox(height: 6),
          Card(
            margin: EdgeInsets.zero,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(child: _SortDropdown(value: sortBy, onChanged: onSortChanged)),
                      const SizedBox(width: 8),
                      IconButton.filledTonal(
                        onPressed: () => onDirectionChanged(!ascending),
                        icon: Icon(ascending ? Icons.arrow_upward : Icons.arrow_downward),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  DropdownButtonFormField<String>(
                    value: selectedCategory,
                    decoration: const InputDecoration(
                      labelText: '카테고리',
                      prefixIcon: Icon(Icons.category_outlined),
                    ),
                    items: [
                      const DropdownMenuItem(value: '', child: Text('전체 카테고리')),
                      ...categories.map((e) => DropdownMenuItem(value: e, child: Text(e))),
                    ],
                    onChanged: (v) => vm.setFilterCategory(v ?? ''),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      SizedBox(
                        width: 120,
                        child: _PageSizeDropdown(value: pageSize, onChanged: onPageSizeChanged),
                      ),
                      const Spacer(),
                      Text('$currentPage / $totalPages'),
                      const SizedBox(width: 8),
                      IconButton.filledTonal(
                        onPressed: onPreviousPage,
                        icon: const Icon(Icons.chevron_left),
                      ),
                      const SizedBox(width: 4),
                      IconButton.filledTonal(
                        onPressed: onNextPage,
                        icon: const Icon(Icons.chevron_right),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _SearchField extends StatefulWidget {
  const _SearchField({required this.onChanged});
  final ValueChanged<String> onChanged;

  @override
  State<_SearchField> createState() => _SearchFieldState();
}

class _SearchFieldState extends State<_SearchField> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _controller,
      decoration: InputDecoration(
        hintText: 'VOC 번호, 제목, 고객명으로 검색',
        prefixIcon: const Icon(Icons.search),
        suffixIcon: _controller.text.isEmpty
            ? null
            : IconButton(
                onPressed: () {
                  _controller.clear();
                  widget.onChanged('');
                  setState(() {});
                },
                icon: const Icon(Icons.close),
              ),
      ),
      onChanged: (value) {
        widget.onChanged(value);
        setState(() {});
      },
    );
  }
}

class _SortDropdown extends StatelessWidget {
  const _SortDropdown({required this.value, required this.onChanged});
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<String>(
      value: value,
      isDense: true,
      decoration: const InputDecoration(labelText: '정렬', prefixIcon: Icon(Icons.sort)),
      items: const [
        DropdownMenuItem(value: 'latest', child: Text('최신순')),
        DropdownMenuItem(value: 'updated', child: Text('수정일순')),
        DropdownMenuItem(value: 'title', child: Text('제목순')),
        DropdownMenuItem(value: 'customer', child: Text('고객명순')),
        DropdownMenuItem(value: 'vocNumber', child: Text('VOC 번호순')),
        DropdownMenuItem(value: 'priority', child: Text('우선순위순')),
        DropdownMenuItem(value: 'status', child: Text('상태순')),
      ],
      onChanged: (v) {
        if (v != null) onChanged(v);
      },
    );
  }
}

class _PageSizeDropdown extends StatelessWidget {
  const _PageSizeDropdown({required this.value, required this.onChanged});
  final int value;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<int>(
      value: value,
      isDense: true,
      decoration: const InputDecoration(labelText: '표시'),
      items: const [
        DropdownMenuItem(value: 10, child: Text('10건')),
        DropdownMenuItem(value: 25, child: Text('25건')),
        DropdownMenuItem(value: 100, child: Text('100건')),
        DropdownMenuItem(value: _VocListScreenState._pageSizeAll, child: Text('전체')),
      ],
      onChanged: (v) {
        if (v != null) onChanged(v);
      },
    );
  }
}

class _StatusFilters extends StatelessWidget {
  const _StatusFilters({required this.vm});
  final VocViewModel vm;

  static const options = ['', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'];

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 7,
      runSpacing: 7,
      children: options.map((status) {
        final selected = vm.filterStatus == status || (status.isEmpty && vm.filterStatus.isEmpty);
        final color = status.isEmpty ? null : AppTheme.statusColor(status);
        return FilterChip(
          label: Text(_label(status)),
          selected: selected,
          onSelected: (_) => vm.setFilterStatus(status),
          selectedColor: (color ?? Theme.of(context).colorScheme.primary).withValues(alpha: .16),
          checkmarkColor: color ?? Theme.of(context).colorScheme.primary,
          visualDensity: VisualDensity.compact,
        );
      }).toList(),
    );
  }

  String _label(String status) {
    switch (status) {
      case 'OPEN':
        return '미처리';
      case 'IN_PROGRESS':
        return '처리중';
      case 'RESOLVED':
        return '해결';
      case 'REJECTED':
        return '반려';
      default:
        return '전체';
    }
  }
}

class _VocDesktopTable extends StatelessWidget {
  const _VocDesktopTable({required this.vocs});
  final List<VocEntity> vocs;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: SingleChildScrollView(
        child: SizedBox(
          width: double.infinity,
          child: DataTable(
            showCheckboxColumn: false,
            headingRowHeight: 48,
            dataRowMinHeight: 52,
            dataRowMaxHeight: 58,
            headingRowColor: WidgetStatePropertyAll(cs.surfaceContainerLow),
            columns: const [
              DataColumn(label: Text('VOC 번호')),
              DataColumn(label: Text('제목')),
              DataColumn(label: Text('고객')),
              DataColumn(label: Text('카테고리')),
              DataColumn(label: Text('우선순위')),
              DataColumn(label: Text('상태')),
              DataColumn(label: Text('등록일')),
            ],
            rows: vocs.map((voc) {
              final date = '${voc.createdAt.year}-${voc.createdAt.month.toString().padLeft(2, '0')}-${voc.createdAt.day.toString().padLeft(2, '0')}';
              return DataRow(
                onSelectChanged: (_) async {
                  await Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => VocDetailScreen(vocId: voc.id)),
                  );
                  if (context.mounted) context.read<VocViewModel>().loadVocs();
                },
                cells: [
                  DataCell(Text(_number(voc))),
                  DataCell(SizedBox(
                    width: 330,
                    child: Text(voc.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                  )),
                  DataCell(SizedBox(
                    width: 160,
                    child: Text(voc.customer, maxLines: 1, overflow: TextOverflow.ellipsis),
                  )),
                  DataCell(Text(voc.category)),
                  DataCell(PriorityChip(priority: voc.priority)),
                  DataCell(VocStatusChip(status: voc.status)),
                  DataCell(Text(date)),
                ],
              );
            }).toList(),
          ),
        ),
      ),
    );
  }

  static String _number(VocEntity voc) {
    final parts = voc.project.split('|').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
    return parts.isEmpty ? '-' : parts.last;
  }
}

class _VocCard extends StatelessWidget {
  const _VocCard({required this.voc});
  final VocEntity voc;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => VocDetailScreen(vocId: voc.id)),
        ).then((_) => context.read<VocViewModel>().loadVocs()),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      voc.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
                    ),
                  ),
                  const SizedBox(width: 8),
                  VocStatusChip(status: voc.status),
                ],
              ),
              const SizedBox(height: 7),
              Text(voc.content, maxLines: 2, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  _Meta(icon: Icons.person_outline, text: voc.customer),
                  _Meta(icon: Icons.category_outlined, text: voc.category),
                  PriorityChip(priority: voc.priority),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Meta extends StatelessWidget {
  const _Meta({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest.withValues(alpha: .5),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: cs.onSurfaceVariant),
          const SizedBox(width: 4),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 180),
            child: Text(text, maxLines: 1, overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.onRegister});
  final VoidCallback onRegister;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.inbox_outlined, size: 58, color: cs.outline),
            const SizedBox(height: 14),
            Text('조건에 맞는 VOC가 없습니다',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            Text(
              '검색어나 필터를 변경하거나 새 VOC를 등록해 주세요.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
            ),
            const SizedBox(height: 16),
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
