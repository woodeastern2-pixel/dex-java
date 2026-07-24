import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_theme.dart';
import '../../viewmodels/settings_viewmodel.dart';
import '../../viewmodels/voc_viewmodel.dart';
import '../../viewmodels/dashboard_viewmodel.dart';
import '../../../domain/entities/voc_entity.dart';
import '../../widgets/voc_status_chip.dart';
import '../../widgets/priority_chip.dart';
import 'voc_register_screen.dart';
import 'voc_detail_screen.dart';

class VocListScreen extends StatefulWidget {
  final String initialStatus;
  final String initialCategory;

  const VocListScreen({
    super.key,
    this.initialStatus = '',
    this.initialCategory = '',
  });

  @override
  State<VocListScreen> createState() => _VocListScreenState();
}

class _VocListScreenState extends State<VocListScreen> {
  String _sortBy = 'latest';
  bool _ascending = false;
  static const int _pageSizeAll = -1;
  int _pageSize = 25;
  int _currentPage = 1;
  bool _controlsCollapsed = false;

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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('VOC 목록'),
        actions: [
          IconButton(
            icon: Icon(
              _controlsCollapsed
                  ? Icons.unfold_more
                  : Icons.unfold_less,
            ),
            tooltip: _controlsCollapsed ? '검색/정렬 펼치기' : '검색/정렬 접기',
            onPressed: () {
              setState(() {
                _controlsCollapsed = !_controlsCollapsed;
              });
            },
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<VocViewModel>().loadVocs(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const VocRegisterScreen()),
        ).then((_) {
          context.read<VocViewModel>().loadVocs();
          context.read<DashboardViewModel>().loadDashboard();
        }),
        icon: const Icon(Icons.add),
        label: const Text('VOC 등록'),
      ),
      body: Consumer<VocViewModel>(
        builder: (context, vm, _) {
          final sortedVocs = _sortVocList(vm.vocs);
          final totalPages = _calculateTotalPages(sortedVocs.length);
          final currentPage = _normalizePage(totalPages);
          final pagedVocs = _paginate(sortedVocs, currentPage);

          if (currentPage != _currentPage) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (!mounted) return;
              setState(() => _currentPage = currentPage);
            });
          }

          return Column(
            children: [
              Container(
                margin: const EdgeInsets.fromLTRB(12, 6, 12, 4),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  color: Theme.of(context)
                      .colorScheme
                      .surfaceContainerHighest
                      .withValues(alpha: 0.35),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.tune,
                      size: 18,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _controlsCollapsed
                            ? '검색/정렬 설정이 접혀 있습니다'
                            : '검색/정렬 설정',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ),
                    TextButton.icon(
                      onPressed: () {
                        setState(() {
                          _controlsCollapsed = !_controlsCollapsed;
                        });
                      },
                      icon: Icon(
                        _controlsCollapsed
                            ? Icons.keyboard_arrow_down
                            : Icons.keyboard_arrow_up,
                      ),
                      label: Text(_controlsCollapsed ? '펼치기' : '접기'),
                    ),
                  ],
                ),
              ),
              if (!_controlsCollapsed)
                _SearchSortBar(
                  sortBy: _sortBy,
                  ascending: _ascending,
                  onSortChanged: (value) => setState(() {
                    _sortBy = value;
                    _currentPage = 1;
                  }),
                  onDirectionChanged: (value) => setState(() => _ascending = value),
                ),
              _StatusCategoryBar(vm: vm),
              if (!vm.isLoading)
                _PaginationBar(
                  totalCount: sortedVocs.length,
                  currentPage: currentPage,
                  totalPages: totalPages,
                  pageSize: _pageSize,
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
              Expanded(
                child: vm.isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : pagedVocs.isEmpty
                        ? const _EmptyState()
                        : RefreshIndicator(
                            onRefresh: vm.loadVocs,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(
                              left: 12, right: 12, bottom: 80),
                              itemCount: pagedVocs.length,
                              itemBuilder: (_, i) =>
                                  _VocCard(voc: pagedVocs[i]),
                            ),
                          ),
              ),
            ],
          );
        },
      ),
    );
  }

  int _calculateTotalPages(int totalCount) {
    if (totalCount == 0) return 1;
    if (_pageSize == _pageSizeAll) return 1;
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
    final end = (start + _pageSize).clamp(0, list.length);
    return list.sublist(start, end);
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
        case 'latest':
        default:
          return a.createdAt.compareTo(b.createdAt);
      }
    }

    list.sort(compare);
    if (!_ascending) {
      return list.reversed.toList();
    }
    return list;
  }

  int _compareVocNumber(VocEntity a, VocEntity b) {
    final aNumber = _vocNumberNumericValue(a);
    final bNumber = _vocNumberNumericValue(b);

    if (aNumber != null && bNumber != null) {
      final numberCompare = aNumber.compareTo(bNumber);
      if (numberCompare != 0) {
        return numberCompare;
      }
    } else if (aNumber != null) {
      return -1;
    } else if (bNumber != null) {
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
    final token = _vocNumberTokenForSort(voc);
    if (token.isEmpty) return null;

    final match = RegExp(r'(\d+)').firstMatch(token);
    if (match == null) return null;
    return int.tryParse(match.group(1)!);
  }

  String _vocNumberTokenForSort(VocEntity voc) {
    final parts = voc.project.split('|').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
    if (parts.isNotEmpty) {
      return parts.last.toUpperCase();
    }
    return '';
  }
}

class _SearchSortBar extends StatefulWidget {
  final String sortBy;
  final bool ascending;
  final ValueChanged<String> onSortChanged;
  final ValueChanged<bool> onDirectionChanged;
  const _SearchSortBar({
    required this.sortBy,
    required this.ascending,
    required this.onSortChanged,
    required this.onDirectionChanged,
  });

  @override
  State<_SearchSortBar> createState() => _SearchSortBarState();
}

class _SearchSortBarState extends State<_SearchSortBar> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Column(
        children: [
          TextField(
            controller: _controller,
            decoration: InputDecoration(
              hintText: 'VOC 검색...',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _controller.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear),
                      onPressed: () {
                        _controller.clear();
                        context.read<VocViewModel>().setSearch('');
                      },
                    )
                  : null,
              isDense: true,
            ),
            onChanged: context.read<VocViewModel>().setSearch,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: widget.sortBy,
                  decoration: const InputDecoration(
                    labelText: '정렬',
                    prefixIcon: Icon(Icons.sort),
                    isDense: true,
                  ),
                  items: const [
                    DropdownMenuItem(value: 'latest', child: Text('최신순')),
                    DropdownMenuItem(value: 'updated', child: Text('수정일순')),
                    DropdownMenuItem(value: 'title', child: Text('제목순')),
                    DropdownMenuItem(value: 'customer', child: Text('고객명순')),
                    DropdownMenuItem(value: 'vocNumber', child: Text('VOC 번호순')),
                    DropdownMenuItem(value: 'priority', child: Text('우선순위순')),
                    DropdownMenuItem(value: 'status', child: Text('상태순')),
                  ],
                  onChanged: (value) {
                    if (value != null) widget.onSortChanged(value);
                  },
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filledTonal(
                onPressed: () => widget.onDirectionChanged(!widget.ascending),
                icon: Icon(
                  widget.ascending ? Icons.arrow_upward : Icons.arrow_downward,
                ),
                tooltip: widget.ascending ? '오름차순' : '내림차순',
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatusCategoryBar extends StatelessWidget {
  final VocViewModel vm;

  const _StatusCategoryBar({required this.vm});

  static const List<String> _statusOptions = [
    '',
    'OPEN',
    'IN_PROGRESS',
    'RESOLVED',
    'REJECTED',
  ];

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsViewModel>().allCategories;
    final selectedCategory = categories.contains(vm.filterCategory)
        ? vm.filterCategory
        : '';

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 2),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: _statusOptions.map((status) {
              final isAll = status.isEmpty;
              final selected = vm.filterStatus == status ||
                  (isAll && vm.filterStatus.isEmpty);
              return _FilterChip(
                label: isAll ? '전체' : _statusLabel(status),
                selected: selected,
                onTap: () => vm.setFilterStatus(status),
                color: isAll ? null : AppTheme.statusColor(status),
              );
            }).toList(),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: selectedCategory,
            isDense: true,
            decoration: const InputDecoration(
              labelText: '카테고리',
              prefixIcon: Icon(Icons.category_outlined),
            ),
            items: [
              const DropdownMenuItem(value: '', child: Text('전체 카테고리')),
              ...categories.map(
                (category) => DropdownMenuItem(
                  value: category,
                  child: Text(category),
                ),
              ),
            ],
            onChanged: (value) => vm.setFilterCategory(value ?? ''),
          ),
        ],
      ),
    );
  }

  String _statusLabel(String s) {
    switch (s) {
      case 'OPEN':
        return '미처리';
      case 'IN_PROGRESS':
        return '처리중';
      case 'RESOLVED':
        return '해결';
      case 'REJECTED':
        return '반려';
      default:
        return s;
    }
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final Color? color;
  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onTap,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: FilterChip(
        label: Text(label, style: const TextStyle(fontSize: 12)),
        selected: selected,
        onSelected: (_) => onTap(),
        selectedColor: (color ?? Theme.of(context).colorScheme.primary)
            .withValues(alpha: 0.2),
        checkmarkColor: color ?? Theme.of(context).colorScheme.primary,
        visualDensity: VisualDensity.compact,
      ),
    );
  }
}

class _PaginationBar extends StatelessWidget {
  final int totalCount;
  final int currentPage;
  final int totalPages;
  final int pageSize;
  final ValueChanged<int> onPageSizeChanged;
  final VoidCallback? onPreviousPage;
  final VoidCallback? onNextPage;

  const _PaginationBar({
    required this.totalCount,
    required this.currentPage,
    required this.totalPages,
    required this.pageSize,
    required this.onPageSizeChanged,
    required this.onPreviousPage,
    required this.onNextPage,
  });

  @override
  Widget build(BuildContext context) {
    const pageSizeOptions = [10, 25, 100, _VocListScreenState._pageSizeAll];
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Theme.of(context)
            .colorScheme
            .surfaceContainerHighest
            .withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '총 $totalCount건 · 페이지 $currentPage / $totalPages',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              SizedBox(
                width: 150,
                child: DropdownButtonFormField<int>(
                  value: pageSize,
                  isDense: true,
                  decoration: const InputDecoration(
                    labelText: '페이지 크기',
                    border: OutlineInputBorder(),
                  ),
                  items: pageSizeOptions
                      .map(
                        (value) => DropdownMenuItem<int>(
                          value: value,
                          child: Text(
                            value == _VocListScreenState._pageSizeAll
                                ? '전체보기'
                                : '${value}건',
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: (value) {
                    if (value != null) onPageSizeChanged(value);
                  },
                ),
              ),
              const Spacer(),
              IconButton.filledTonal(
                icon: const Icon(Icons.chevron_left),
                tooltip: '이전 페이지',
                onPressed: onPreviousPage,
              ),
              const SizedBox(width: 6),
              IconButton.filledTonal(
                icon: const Icon(Icons.chevron_right),
                tooltip: '다음 페이지',
                onPressed: onNextPage,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _VocCard extends StatelessWidget {
  final VocEntity voc;
  const _VocCard({required this.voc});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 6),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => VocDetailScreen(vocId: voc.id),
          ),
        ).then((_) => context.read<VocViewModel>().loadVocs()),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      voc.title,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 8),
                  VocStatusChip(status: voc.status),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                voc.content,
                style: Theme.of(context).textTheme.bodySmall,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                runSpacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  _MetaPill(
                    icon: Icons.person_outline,
                    text: voc.customer,
                  ),
                  _MetaPill(
                    icon: Icons.folder_outlined,
                    text: voc.project,
                  ),
                  Chip(
                    label: Text(voc.category,
                        style: const TextStyle(fontSize: 10)),
                    visualDensity: VisualDensity.compact,
                    padding: EdgeInsets.zero,
                  ),
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

class _MetaPill extends StatelessWidget {
  final IconData icon;
  final String text;

  const _MetaPill({
    required this.icon,
    required this.text,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Theme.of(context)
            .colorScheme
            .surfaceContainerHighest
            .withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 13,
            color: Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(width: 4),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 220),
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.inbox_outlined, size: 64,
              color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: 16),
          Text('VOC가 없습니다',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Text('우측 하단 버튼으로 VOC를 등록하세요',
              style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}
