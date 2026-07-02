import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
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
            icon: const Icon(Icons.upload_file_outlined),
            tooltip: '시스템 매뉴얼 업로드',
            onPressed: () => _pickAndImportDocuments(context),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<KnowledgeBaseViewModel>().loadEntries(),
          ),
        ],
      ),
      body: Consumer<KnowledgeBaseViewModel>(
        builder: (context, vm, _) => Column(
          children: [
            if (vm.isImportingManual)
              const LinearProgressIndicator(minHeight: 2),
            _SearchBar(vm: vm),
            _CategoryFilter(vm: vm),
            _ManualUploadManager(vm: vm),
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

  Future<void> _pickAndImportDocuments(BuildContext context) async {
    final vm = context.read<KnowledgeBaseViewModel>();
    final selected = await FilePicker.platform.pickFiles(
      allowMultiple: true,
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'docx', 'xlsx', 'pptx', 'doc', 'xls', 'ppt'],
      withData: false,
    );

    if (selected == null || selected.files.isEmpty) {
      return;
    }

    final paths = selected.files
        .map((file) => file.path)
        .whereType<String>()
        .toList();

    if (paths.isEmpty) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('선택한 파일 경로를 읽지 못했습니다.')),
      );
      return;
    }

    final result = await vm.importManualDocuments(paths);
    if (!context.mounted || result == null) {
      return;
    }

    final warningText = result.warnings.isEmpty
        ? ''
        : '\n경고 ${result.warnings.length}건: ${result.warnings.first}';

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        duration: const Duration(seconds: 4),
        content: Text(
          '업로드 ${result.selectedFiles}건 중 ${result.processedFiles}건 처리, 신규 ${result.importedEntries}건, 갱신 ${result.updatedEntries}건$warningText',
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

class _ManualUploadManager extends StatelessWidget {
  final KnowledgeBaseViewModel vm;

  const _ManualUploadManager({required this.vm});

  @override
  Widget build(BuildContext context) {
    final grouped = vm.manualEntriesByFile;
    if (grouped.isEmpty) {
      return const SizedBox.shrink();
    }

    return Card(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.folder_zip_outlined, size: 18),
                const SizedBox(width: 8),
                Text('업로드된 시스템 매뉴얼', style: Theme.of(context).textTheme.titleSmall),
              ],
            ),
            const SizedBox(height: 6),
            const Text(
              '파일명 기준으로 묶여 있으며, 삭제 시 해당 파일에서 생성된 섹션이 모두 제거됩니다.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 10),
            ...grouped.entries.map(
              (entry) => ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.description_outlined, size: 18),
                title: Text(entry.key, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text('섹션 ${entry.value}건', style: const TextStyle(fontSize: 12)),
                trailing: IconButton(
                  icon: const Icon(Icons.delete_outline, color: Colors.red),
                  tooltip: '파일 기반 매뉴얼 삭제',
                  onPressed: () => _confirmDeleteGroup(context, vm, entry.key, entry.value),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmDeleteGroup(
    BuildContext context,
    KnowledgeBaseViewModel vm,
    String fileName,
    int count,
  ) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('매뉴얼 그룹 삭제'),
        content: Text('$fileName 파일로 등록된 섹션 ${count}건을 삭제하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('삭제'),
          ),
        ],
      ),
    );

    if (confirm != true || !context.mounted) return;
    final deleted = await vm.deleteManualEntriesByFile(fileName);
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$fileName 매뉴얼 섹션 ${deleted}건을 삭제했습니다.')),
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
