import 'package:flutter/material.dart';
import '../../models/student_message.dart';
import '../../services/student_service.dart';
import '../../widgets/student_bottom_nav.dart';

class StudentMessageCenterScreen extends StatefulWidget {
  const StudentMessageCenterScreen({super.key});

  @override
  State<StudentMessageCenterScreen> createState() =>
      _StudentMessageCenterScreenState();
}

class _StudentMessageCenterScreenState
    extends State<StudentMessageCenterScreen> {
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
      final result =
          await StudentService.getStudentMessages(unreadOnly: _unreadOnly);
      if (!mounted) return;
      setState(() => _messages = result);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
      } catch (_) {}
    }

    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(msg.subject?.isNotEmpty == true ? msg.subject! : '消息详情'),
        content: Text(
          (msg.content == null || msg.content!.trim().isEmpty)
              ? '暂无内容'
              : msg.content!,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('关闭'),
          ),
        ],
      ),
    );
    if (mounted) {
      _load();
    }
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('消息中心')),
      body: Column(
        children: [
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                ChoiceChip(
                  label: const Text('全部'),
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
                  label: const Text('仅未读'),
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
                : _messages.isEmpty
                    ? const Center(child: Text('暂无消息'))
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.builder(
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
                                  color: msg.isRead
                                      ? Colors.grey
                                      : Colors.blueAccent,
                                ),
                                title: Text(msg.subject ?? '（无主题）'),
                                subtitle: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const SizedBox(height: 4),
                                    Text(
                                      (msg.content == null ||
                                              msg.content!.trim().isEmpty)
                                          ? '暂无内容'
                                          : msg.content!,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      _formatDate(msg.sentAt),
                                      style: const TextStyle(
                                        fontSize: 12,
                                        color: Colors.grey,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
      bottomNavigationBar: const StudentBottomNav(currentIndex: 2),
    );
  }
}
