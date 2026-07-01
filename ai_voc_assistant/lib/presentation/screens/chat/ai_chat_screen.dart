import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../../viewmodels/ai_viewmodel.dart';

class AiChatScreen extends StatelessWidget {
  const AiChatScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const _AiChatListScreen();
  }
}

class _AiChatListScreen extends StatefulWidget {
  const _AiChatListScreen();

  @override
  State<_AiChatListScreen> createState() => _AiChatListScreenState();
}

class _AiChatListScreenState extends State<_AiChatListScreen> {
  bool _isLoading = true;
  List<AiChatSessionSummary> _sessions = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadSessions());
  }

  Future<void> _loadSessions() async {
    setState(() => _isLoading = true);
    final sessions = await context.read<AiViewModel>().loadChatSessions();
    if (!mounted) return;
    setState(() {
      _sessions = sessions;
      _isLoading = false;
    });
  }

  Future<void> _openSession(AiChatSessionSummary session) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => _AiChatConversationScreen(
          sessionId: session.sessionId,
          title: session.title,
        ),
      ),
    );
    if (!mounted) return;
    await _loadSessions();
  }

  Future<void> _createNewSession() async {
    final sessionId = await context.read<AiViewModel>().createChatSession();
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => _AiChatConversationScreen(
          sessionId: sessionId,
          title: '새 채팅',
        ),
      ),
    );
    if (!mounted) return;
    await _loadSessions();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Chat'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _createNewSession,
        tooltip: '새 채팅',
        child: const Icon(Icons.add),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _sessions.isEmpty
              ? const Center(child: Text('채팅 목록이 없습니다. + 버튼으로 새 채팅을 시작하세요.'))
              : RefreshIndicator(
                  onRefresh: _loadSessions,
                  child: ListView.separated(
                    itemCount: _sessions.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final session = _sessions[index];
                      return ListTile(
                        leading: const CircleAvatar(child: Icon(Icons.chat_bubble_outline)),
                        title: Text(
                          session.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          session.preview,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing: Text('${session.messageCount}'),
                        onTap: () => _openSession(session),
                      );
                    },
                  ),
                ),
    );
  }
}

class _AiChatConversationScreen extends StatefulWidget {
  final String sessionId;
  final String title;

  const _AiChatConversationScreen({
    required this.sessionId,
    required this.title,
  });

  @override
  State<_AiChatConversationScreen> createState() => _AiChatConversationScreenState();
}

class _AiChatConversationScreenState extends State<_AiChatConversationScreen> {
  final _controller = TextEditingController();
  final _scrollController = ScrollController();
  final _focusNode = FocusNode();
  final _speechToText = SpeechToText();
  bool _speechAvailable = false;
  bool _isListening = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await context.read<AiViewModel>().startChatSession(widget.sessionId);
      await _initSpeech();
      if (mounted) {
        _focusNode.requestFocus();
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    _focusNode.dispose();
    _speechToText.stop();
    super.dispose();
  }

  Future<void> _initSpeech() async {
    final available = await _speechToText.initialize();
    if (!mounted) return;
    setState(() => _speechAvailable = available);
  }

  Future<void> _toggleListening() async {
    if (!_speechAvailable) return;

    if (_isListening) {
      await _speechToText.stop();
      if (!mounted) return;
      setState(() => _isListening = false);
      _focusNode.requestFocus();
      return;
    }

    final started = await _speechToText.listen(
      onResult: (result) {
        if (!mounted) return;
        setState(() {
          _controller.text = result.recognizedWords;
          _controller.selection = TextSelection.fromPosition(
            TextPosition(offset: _controller.text.length),
          );
        });
      },
      partialResults: true,
      cancelOnError: true,
    );
    if (!mounted) return;
    setState(() => _isListening = started);
  }

  Future<void> _send() async {
    final message = _controller.text.trim();
    if (message.isEmpty) {
      _focusNode.requestFocus();
      return;
    }
    _controller.clear();
    await context.read<AiViewModel>().sendChatMessage(message);
    _scrollToBottom();
    _focusNode.requestFocus();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<AiViewModel>(
      builder: (context, vm, _) {
        final messages = vm.chatMessages;
        if (messages.isNotEmpty) {
          _scrollToBottom();
        }

        return Scaffold(
          appBar: AppBar(
            title: Text(widget.title),
          ),
          body: Column(
            children: [
              if (vm.chatError != null)
                Container(
                  width: double.infinity,
                  color: Theme.of(context).colorScheme.errorContainer,
                  padding: const EdgeInsets.all(12),
                  child: Text(
                    vm.chatError!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error),
                  ),
                ),
              Expanded(
                child: ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.all(16),
                  itemCount: messages.length,
                  itemBuilder: (context, index) {
                    final message = messages[index];
                    final isUser = message.role == 'user';
                    return Align(
                      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
                      child: Container(
                        margin: const EdgeInsets.only(bottom: 12),
                        padding: const EdgeInsets.all(12),
                        constraints: const BoxConstraints(maxWidth: 720),
                        decoration: BoxDecoration(
                          color: isUser
                              ? Theme.of(context).colorScheme.primaryContainer
                              : Theme.of(context).colorScheme.surfaceVariant,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          crossAxisAlignment:
                              isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                          children: [
                            Text(
                              isUser ? '사용자' : 'AI',
                              style: Theme.of(context).textTheme.labelSmall,
                            ),
                            const SizedBox(height: 6),
                            Text(message.content, style: const TextStyle(height: 1.5)),
                            if (!isUser && message.referencedVocIds.isNotEmpty) ...[
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 6,
                                runSpacing: 6,
                                children: message.referencedVocIds
                                    .map((vocId) => Chip(label: Text(vocId, overflow: TextOverflow.ellipsis)))
                                    .toList(),
                              ),
                            ],
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
              SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _controller,
                          focusNode: _focusNode,
                          autofocus: true,
                          minLines: 1,
                          maxLines: 4,
                          textInputAction: TextInputAction.send,
                          onSubmitted: (_) {
                            if (!vm.isChatting) {
                              _send();
                            }
                          },
                          decoration: const InputDecoration(
                            hintText: '지원 관련 질문을 입력하세요',
                            border: OutlineInputBorder(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton.filledTonal(
                        onPressed: _speechAvailable ? _toggleListening : null,
                        tooltip: _isListening ? '음성 입력 중지' : '음성 입력',
                        icon: Icon(_isListening ? Icons.mic : Icons.mic_none),
                      ),
                      const SizedBox(width: 12),
                      FilledButton(
                        onPressed: vm.isChatting ? null : _send,
                        child: vm.isChatting
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.send),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}