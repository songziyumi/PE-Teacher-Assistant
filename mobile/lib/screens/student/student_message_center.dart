import 'package:flutter/material.dart';

import '../../models/student_message.dart';
import '../../services/student_service.dart';
import '../../widgets/student_bottom_nav.dart';
import 'student_message_compose.dart';
import 'student_message_detail.dart';

class StudentMessageCenterScreen extends StatefulWidget {
  const StudentMessageCenterScreen({super.key});

  @override
  State<StudentMessageCenterScreen> createState() =>
      _StudentMessageCenterScreenState();
}

class _StudentMessageCenterScreenState
    extends State<StudentMessageCenterScreen> {
  static const _title = '消息中心';
  static const _allLabel = '全部';
  static const _unreadOnlyLabel = '仅未读';
  static const _emptyListText = '暂无消息';
  static const _untitledText = '（无主题）';
  static const _emptyContentText = '暂无内容';
  static const _composeLabel = '发消息';
  static const _loadFailedPrefix = '加载失败：';
  static const _markReadFailedPrefix = '标记已读失败：';
  static const _sendSuccessText = '消息已发送';

  bool _loading = true;
  bool _unreadOnly = false;
  List<StudentMessage> _messages = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final messages = await StudentService.getStudentMessages(
        unreadOnly: _unreadOnly,
      );
      if (!mounted) return;
      setState(() => _messages = messages);
    } catch (e) {
      if (!mounted) return;
      _showSnackBar('$_loadFailedPrefix$e');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  void _showSnackBar(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _onTapMessage(StudentMessage msg) async {
    if (!msg.isRead) {
      try {
        await StudentService.markStudentMessageRead(msg.id);
        if (mounted) {
          setState(() {
            _messages = _messages
                .map((m) => m.id == msg.id
                    ? StudentMessage(
                        id: m.id,
                        subject: m.subject,
                        content: m.content,
                        type: m.type,
                        status: m.status,
                        isRead: true,
                        sentAt: m.sentAt,
                        senderType: m.senderType,
                        senderId: m.senderId,
                        senderName: m.senderName,
                        relatedCourseId: m.relatedCourseId,
                        relatedCourseName: m.relatedCourseName,
                      )
                    : m)
                .toList();
          });
        }
      } catch (e) {
        _showSnackBar('$_markReadFailedPrefix$e');
      }
    }

    if (!mounted) return;
    final replied = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => StudentMessageDetailScreen(message: msg),
      ),
    );

    if (mounted) {
      if (replied == true) {
        _showSnackBar(_sendSuccessText);
      }
      _load();
    }
  }

  Future<void> _openComposePage() async {
    final sent = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => const StudentMessageComposeScreen(),
      ),
    );

    if (sent == true) {
      _showSnackBar(_sendSuccessText);
      _load();
    }
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  Widget _buildMessageList() {
    if (_messages.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(12),
        children: const [
          SizedBox(height: 120),
          Center(child: Text(_emptyListText)),
        ],
      );
    }

    return ListView.builder(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(12),
      itemCount: _messages.length,
      itemBuilder: (_, i) {
        final msg = _messages[i];
        return Card(
          margin: const EdgeInsets.only(bottom: 10),
          child: ListTile(
            onTap: () => _onTapMessage(msg),
            leading: Icon(
              Icons.mail,
              color: msg.isRead ? Colors.grey : Colors.blueAccent,
            ),
            title: Text(msg.subject ?? _untitledText),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 4),
                Text(
                  (msg.content == null || msg.content!.trim().isEmpty)
                      ? _emptyContentText
                      : msg.content!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  _formatDate(msg.sentAt),
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text(_title)),
      body: Column(
        children: [
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                ChoiceChip(
                  label: const Text(_allLabel),
                  selected: !_unreadOnly,
                  onSelected: (_) {
                    if (_unreadOnly) {
                      setState(() => _unreadOnly = false);
                      _load();
                    }
                  },
                ),
                const SizedBox(width: 8),
                ChoiceChip(
                  label: const Text(_unreadOnlyLabel),
                  selected: _unreadOnly,
                  onSelected: (_) {
                    if (!_unreadOnly) {
                      setState(() => _unreadOnly = true);
                      _load();
                    }
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : RefreshIndicator(onRefresh: _load, child: _buildMessageList()),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _loading ? null : _openComposePage,
        icon: const Icon(Icons.edit_outlined),
        label: const Text(_composeLabel),
      ),
      bottomNavigationBar: const StudentBottomNav(currentIndex: 2),
    );
  }
}
