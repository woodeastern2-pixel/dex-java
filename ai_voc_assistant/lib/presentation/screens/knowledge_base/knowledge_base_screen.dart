import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../viewmodels/knowledge_base_viewmodel.dart';

class KnowledgeBaseScreen extends StatelessWidget {
  const KnowledgeBaseScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('지식베이스'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<KnowledgeBaseViewModel>().loadEntries(),
          ),
        ],
      ),
      body: Consumer<KnowledgeBaseViewModel>(
        builder: (context, vm, _) => Column(
          children: [
            _SearchBar(vm: vm),
            _CategoryFilter(vm: vm),
            Expanded(
              child: vm.isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : vm.entries.isEmpty
                      ? const _EmptyState()
                      : RefreshIndicator(
                          onRefresh: vm.loadEntries,
                          child: ListView.builder(
                            padding: const EdgeInsets.only(
                                left: 16, right: 16, bottom: 16),
                            itemCount: vm.entries.length,
                            itemBuilder: (_, i) =>
                                _KbCard(entry: vm.entries[i], vm: vm),
                          ),
                        ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SearchBar extends StatefulWidget {
  final KnowledgeBaseViewModel vm;
  const _SearchBar({required this.vm});

  @override
  State<_SearchBar> createState() => _SearchBarState();
}

class _SearchBarState extends State<_SearchBar> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: TextField(
        controller: _controller,
        decoration: InputDecoration(
          hintText: '지식베이스 검색...',
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
    );
  }
}

class _CategoryFilter extends StatelessWidget {
  final KnowledgeBaseViewModel vm;
  const _CategoryFilter({required this.vm});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Row(
        children: [
          FilterChip(
            label: const Text('전체', style: TextStyle(fontSize: 12)),
            selected: vm.filterCategory.isEmpty,
            onSelected: (_) => vm.setFilter(''),
            visualDensity: VisualDensity.compact,
          ),
          ...vm.categories.map((c) => Padding(
                padding: const EdgeInsets.only(left: 6),
                child: FilterChip(
                  label: Text(c, style: const TextStyle(fontSize: 12)),
                  selected: vm.filterCategory == c,
                  onSelected: (_) => vm.setFilter(c),
                  visualDensity: VisualDensity.compact,
                ),
              )),
        ],
      ),
    );
  }
}

class _KbCard extends StatelessWidget {
  final entry;
  final KnowledgeBaseViewModel vm;
  const _KbCard({required this.entry, required this.vm});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ExpansionTile(
        leading: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.primaryContainer,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(Icons.question_mark,
              size: 18,
              color: Theme.of(context).colorScheme.primary),
        ),
        title: Text(
          entry.question,
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Row(
            children: [
              Chip(
                label: Text(entry.category,
                    style: const TextStyle(fontSize: 10)),
                visualDensity: VisualDensity.compact,
                padding: EdgeInsets.zero,
              ),
              if (entry.customer != null) ...[
                const SizedBox(width: 6),
                Text(entry.customer!,
                    style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ],
          ),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Divider(),
                const Text('답변:',
                    style: TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 12)),
                const SizedBox(height: 4),
                Text(entry.answer, style: const TextStyle(fontSize: 13, height: 1.5)),
                const SizedBox(height: 8),
                Row(
                  children: [
                    if (entry.embedding != null)
                      const Chip(
                        label: Text('임베딩 완료',
                            style: TextStyle(fontSize: 10)),
                        visualDensity: VisualDensity.compact,
                        padding: EdgeInsets.zero,
                      ),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 18),
                      color: Colors.red,
                      onPressed: () => _confirmDelete(context, vm),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _confirmDelete(BuildContext context, KnowledgeBaseViewModel vm) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('삭제 확인'),
        content: const Text('지식베이스 항목을 삭제하시겠습니까?'),
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
    if (confirm == true) {
      await vm.deleteEntry(entry.id);
    }
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
          Icon(Icons.book_outlined, size: 64,
              color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: 16),
          const Text('지식베이스가 비어 있습니다'),
          const SizedBox(height: 8),
          const Text('VOC 답변을 승인하면 자동으로 등록됩니다',
              style: TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ),
    );
  }
}
