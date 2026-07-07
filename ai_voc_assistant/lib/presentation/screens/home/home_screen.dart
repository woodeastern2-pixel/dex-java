import 'package:flutter/material.dart';
import '../dashboard/dashboard_screen.dart';
import '../chat/ai_chat_screen.dart';
import '../voc/voc_list_screen.dart';
import '../knowledge_base/knowledge_base_screen.dart';
import '../jira/jira_screen.dart';
import '../settings/settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 0;

  final _screens = const [
    DashboardScreen(),
    VocListScreen(),
    AiChatScreen(),
    KnowledgeBaseScreen(),
    JiraScreen(),
    SettingsScreen(),
  ];

  final _destinations = const [
    _NavItem(
      icon: Icons.dashboard_outlined,
      selectedIcon: Icons.dashboard,
      label: '대시보드',
      hint: 'KPI / 추이 / 운영 인사이트',
    ),
    _NavItem(
      icon: Icons.inbox_outlined,
      selectedIcon: Icons.inbox,
      label: 'VOC 관리',
      hint: '등록 / 검색 / 처리흐름',
    ),
    _NavItem(
      icon: Icons.chat_bubble_outline,
      selectedIcon: Icons.chat_bubble,
      label: 'AI Chat',
      hint: 'AI 질의 / 답변 초안 생성',
    ),
    _NavItem(
      icon: Icons.book_outlined,
      selectedIcon: Icons.book,
      label: '지식베이스',
      hint: '매뉴얼 / FAQ / 문서자산',
    ),
    _NavItem(
      icon: Icons.link_outlined,
      selectedIcon: Icons.link,
      label: 'JIRA',
      hint: '이슈 연동 / 트래킹',
    ),
    _NavItem(
      icon: Icons.settings_outlined,
      selectedIcon: Icons.settings,
      label: '설정',
      hint: '시스템 / 동기화 / 운영옵션',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.of(context).size.width >= 1100;

    if (isWide) {
      return Scaffold(
        body: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Color(0xFFF3F8FF),
                Color(0xFFF7FAFD),
                Color(0xFFF2FBF9),
              ],
            ),
          ),
          child: Row(
            children: [
              _DesktopSideNav(
                items: _destinations,
                selectedIndex: _selectedIndex,
                onSelect: (i) => setState(() => _selectedIndex = i),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 16, 20, 16),
                  child: Column(
                    children: [
                      _DesktopTopBar(
                        currentLabel: _destinations[_selectedIndex].label,
                        currentHint: _destinations[_selectedIndex].hint,
                      ),
                      const SizedBox(height: 14),
                      Expanded(
                        child: Container(
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.surface,
                            borderRadius: BorderRadius.circular(24),
                            border: Border.all(
                              color: Theme.of(context)
                                  .colorScheme
                                  .outline
                                  .withValues(alpha: 0.28),
                            ),
                            boxShadow: const [
                              BoxShadow(
                                color: Color(0x140F172A),
                                blurRadius: 24,
                                offset: Offset(0, 10),
                              ),
                            ],
                          ),
                          clipBehavior: Clip.antiAlias,
                          child: AnimatedSwitcher(
                            duration: const Duration(milliseconds: 220),
                            child: KeyedSubtree(
                              key: ValueKey(_selectedIndex),
                              child: _screens[_selectedIndex],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('AI VOC Assistant'),
      ),
      body: _screens[_selectedIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (i) => setState(() => _selectedIndex = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard),
            label: '대시보드',
          ),
          NavigationDestination(
            icon: Icon(Icons.inbox_outlined),
            selectedIcon: Icon(Icons.inbox),
            label: 'VOC',
          ),
          NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline),
            selectedIcon: Icon(Icons.chat_bubble),
            label: 'Chat',
          ),
          NavigationDestination(
            icon: Icon(Icons.book_outlined),
            selectedIcon: Icon(Icons.book),
            label: '지식베이스',
          ),
          NavigationDestination(
            icon: Icon(Icons.link_outlined),
            selectedIcon: Icon(Icons.link),
            label: 'JIRA',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: '설정',
          ),
        ],
      ),
    );
  }

}

class _DesktopTopBar extends StatelessWidget {
  final String currentLabel;
  final String currentHint;

  const _DesktopTopBar({
    required this.currentLabel,
    required this.currentHint,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final period = now.hour < 12 ? 'AM' : 'PM';
    final hh = now.hour % 12 == 0 ? 12 : now.hour % 12;
    final mm = now.minute.toString().padLeft(2, '0');
    final stamp = '$period $hh:$mm';

    return Container(
      height: 84,
      padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.8),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.26),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  currentLabel,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  currentHint,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurfaceVariant
                            .withValues(alpha: 0.9),
                      ),
                ),
              ],
            ),
          ),
          const _TopPill(
            icon: Icons.sync_outlined,
            label: 'In-App Sync',
            tone: Color(0xFF0E9F6E),
          ),
          const SizedBox(width: 10),
          _TopPill(
            icon: Icons.schedule,
            label: stamp,
            tone: const Color(0xFF2563EB),
          ),
        ],
      ),
    );
  }
}

class _TopPill extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color tone;

  const _TopPill({
    required this.icon,
    required this.label,
    required this.tone,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: tone.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: tone.withValues(alpha: 0.3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: tone),
          const SizedBox(width: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: tone,
                  fontWeight: FontWeight.w700,
                ),
          ),
        ],
      ),
    );
  }
}

class _DesktopSideNav extends StatelessWidget {
  final List<_NavItem> items;
  final int selectedIndex;
  final ValueChanged<int> onSelect;

  const _DesktopSideNav({
    required this.items,
    required this.selectedIndex,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 280,
      decoration: BoxDecoration(
        color: const Color(0xFF0B253D),
        border: Border(
          right: BorderSide(
            color: Colors.white.withValues(alpha: 0.08),
          ),
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF1D4ED8), Color(0xFF0EA5A5)],
                  ),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.22),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.auto_awesome,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'AI VOC Assistant',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Enterprise Console',
                            style: TextStyle(
                              color: Color(0xFFDBEAFE),
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              Text(
                'WORKSPACE',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: const Color(0xFF93C5FD),
                      letterSpacing: 1.0,
                      fontWeight: FontWeight.w700,
                    ),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: ListView.separated(
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 4),
                  itemBuilder: (context, i) {
                    final item = items[i];
                    final selected = i == selectedIndex;
                    final iconColor = selected
                        ? const Color(0xFFDBEAFE)
                        : const Color(0xFFBFDBFE);
                    return Material(
                      color: selected
                          ? Colors.white.withValues(alpha: 0.14)
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(12),
                      child: InkWell(
                        borderRadius: BorderRadius.circular(12),
                        onTap: () => onSelect(i),
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
                          child: Row(
                            children: [
                              Icon(
                                selected ? item.selectedIcon : item.icon,
                                color: iconColor,
                                size: 20,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  item.label,
                                  style: TextStyle(
                                    color: selected
                                        ? Colors.white
                                        : const Color(0xFFE2E8F0),
                                    fontSize: 13.5,
                                    fontWeight: selected
                                        ? FontWeight.w700
                                        : FontWeight.w600,
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
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem {
  final IconData icon;
  final IconData selectedIcon;
  final String label;
  final String hint;

  const _NavItem({
    required this.icon,
    required this.selectedIcon,
    required this.label,
    required this.hint,
  });
}
