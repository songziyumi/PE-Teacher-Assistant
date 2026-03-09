import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../models/teacher_message.dart';
import '../../services/teacher_service.dart';
import '../../widgets/teacher_bottom_nav.dart';

class TeacherMessageCenterScreen extends StatefulWidget {
  const TeacherMessageCenterScreen({super.key});

  @override
  State<TeacherMessageCenterScreen> createState() =>
      _TeacherMessageCenterScreenState();
}

class _TeacherMessageCenterScreenState
    extends State<TeacherMessageCenterScreen> {
  static const Map<String, String> _typeLabels = {
    'ALL': '全部类型',
    'COURSE_REQUEST': '选课申请',
    'GENERAL': '普通消息',
  };

  bool _loading = true;
  bool _unreadOnly = false;
  String _messageType = 'ALL';
  List<TeacherMessage> _messages = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final result = await TeacherService.getTeacherMessages(
        unreadOnly: _unreadOnly,
        type: _messageType,
      );
      if (!mounted) return;
      setState(() => _messages = result);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载消息失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _onTapMessage(TeacherMessage msg) async {
    final router = GoRouter.of(context);
    if (!msg.isRead) {
      try {
        await TeacherService.markTeacherMessageRead(msg.id);
        final idx = _messages.indexWhere((m) => m.id == msg.id);
        if (idx >= 0 && mounted) {
          setState(() {
            _messages[idx] = TeacherMessage(
              id: msg.id,
              subject: msg.subject,
              content: msg.content,
              type: msg.type,
              status: msg.status,
              isRead: true,
              sentAt: msg.sentAt,
              senderType: msg.senderType,
              senderId: msg.senderId,
              senderName: msg.senderName,
              relatedCourseId: msg.relatedCourseId,
              relatedCourseName: msg.relatedCourseName,
              businessTargetType: msg.businessTargetType,
              businessTargetId: msg.businessTargetId,
            );
          });
        }
      } catch (_) {}
    }

    if (msg.canJumpToCourseRequest) {
      await router.push('/teacher/course-requests/${msg.businessTargetId}');
      if (!mounted) return;
      _load();
      return;
    }

    if (!mounted) return;
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(msg.subject ?? '消息详情'),
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
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('站内消息')),
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
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _typeLabels.entries.map((entry) {
                  final type = entry.key;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(entry.value),
                      selected: _messageType == type,
                      onSelected: (_) {
                        if (_messageType != type) {
                          setState(() => _messageType = type);
                          _load();
                        }
                      },
                    ),
                  );
                }).toList(),
              ),
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
                                  msg.canJumpToCourseRequest
                                      ? Icons.approval
                                      : Icons.mail,
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
                                trailing: msg.canJumpToCourseRequest
                                    ? const Icon(Icons.chevron_right)
                                    : null,
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
      bottomNavigationBar: const TeacherBottomNav(currentIndex: 2),
    );
  }
}
