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
          return Column(
            children: [
              _SearchFilterBar(
                vm: vm,
                sortBy: _sortBy,
                ascending: _ascending,
                onSortChanged: (value) => setState(() => _sortBy = value),
                onDirectionChanged: (value) => setState(() => _ascending = value),
              ),
              Expanded(
                child: vm.isLoading
                    ? const Center(child: CircularProgressIndicator())
                    : sortedVocs.isEmpty
                        ? const _EmptyState()
                        : RefreshIndicator(
                            onRefresh: vm.loadVocs,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(
                                  left: 16, right: 16, bottom: 80),
                              itemCount: sortedVocs.length,
                              itemBuilder: (_, i) =>
                                  _VocCard(voc: sortedVocs[i]),
                            ),
                          ),
              ),
            ],
          );
        },
      ),
    );
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
          return _vocNumberForSort(a).compareTo(_vocNumberForSort(b));
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

  String _vocNumberForSort(VocEntity voc) {
    final parts = voc.project.split('|').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
    if (parts.isNotEmpty) {
      return parts.last.toUpperCase();
    }
    return '';
  }
}

class _SearchFilterBar extends StatefulWidget {
  final VocViewModel vm;
  final String sortBy;
  final bool ascending;
  final ValueChanged<String> onSortChanged;
  final ValueChanged<bool> onDirectionChanged;
  const _SearchFilterBar({
    required this.vm,
    required this.sortBy,
    required this.ascending,
    required this.onSortChanged,
    required this.onDirectionChanged,
  });

  @override
  State<_SearchFilterBar> createState() => _SearchFilterBarState();
}

class _SearchFilterBarState extends State<_SearchFilterBar> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsViewModel>().allCategories;
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
                        widget.vm.setSearch('');
                      },
                    )
                  : null,
              isDense: true,
            ),
            onChanged: widget.vm.setSearch,
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
          const SizedBox(height: 8),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _FilterChip(
                  label: '전체',
                  selected: widget.vm.filterStatus.isEmpty,
                  onTap: () => widget.vm.setFilterStatus(''),
                ),
                ...['OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'].map((s) =>
                  _FilterChip(
                    label: _statusLabel(s),
                    selected: widget.vm.filterStatus == s,
                    onTap: () => widget.vm.setFilterStatus(
                        widget.vm.filterStatus == s ? '' : s),
                    color: AppTheme.statusColor(s),
                  ),
                ),
                const SizedBox(width: 8),
                const VerticalDivider(width: 1),
                const SizedBox(width: 8),
                ...categories.map((c) =>
                  _FilterChip(
                    label: c,
                    selected: widget.vm.filterCategory == c,
                    onTap: () => widget.vm.setFilterCategory(
                        widget.vm.filterCategory == c ? '' : c),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _statusLabel(String s) {
    switch (s) {
      case 'OPEN': return '미처리';
      case 'IN_PROGRESS': return '처리중';
      case 'RESOLVED': return '해결';
      case 'REJECTED': return '반려';
      default: return s;
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
        selectedColor: (color ?? Theme.of(context).colorScheme.primary).withOpacity(0.2),
        checkmarkColor: color ?? Theme.of(context).colorScheme.primary,
        visualDensity: VisualDensity.compact,
      ),
    );
  }
}

class _VocCard extends StatelessWidget {
  final voc;
  const _VocCard({required this.voc});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => VocDetailScreen(vocId: voc.id),
          ),
        ).then((_) => context.read<VocViewModel>().loadVocs()),
        child: Padding(
          padding: const EdgeInsets.all(16),
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
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(Icons.person_outline, size: 14,
                      color: Theme.of(context).colorScheme.outline),
                  const SizedBox(width: 4),
                  Text(voc.customer,
                      style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(width: 12),
                  Icon(Icons.folder_outlined, size: 14,
                      color: Theme.of(context).colorScheme.outline),
                  const SizedBox(width: 4),
                  Text(voc.project,
                      style: Theme.of(context).textTheme.bodySmall),
                  const Spacer(),
                  Chip(
                    label: Text(voc.category,
                        style: const TextStyle(fontSize: 10)),
                    visualDensity: VisualDensity.compact,
                    padding: EdgeInsets.zero,
                  ),
                  const SizedBox(width: 6),
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
