import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../models/teacher_message.dart';
import '../../services/notification_service.dart';
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
    'ALL': '\u5168\u90e8\u7c7b\u578b',
    'COURSE_REQUEST': '\u9009\u8bfe\u7533\u8bf7',
    'GENERAL': '\u666e\u901a\u6d88\u606f',
  };

  static const _messageCenterTitle = '\u7ad9\u5185\u6d88\u606f';
  static const _allLabel = '\u5168\u90e8';
  static const _unreadOnlyLabel = '\u4ec5\u672a\u8bfb';
  static const _messageDetailTitle = '\u6d88\u606f\u8be6\u60c5';
  static const _emptyContentText = '\u6682\u65e0\u5185\u5bb9';
  static const _closeLabel = '\u5173\u95ed';
  static const _emptyListText = '\u6682\u65e0\u6d88\u606f';
  static const _untitledText = '\uff08\u65e0\u4e3b\u9898\uff09';
  static const _loadFailedPrefix = '\u52a0\u8f7d\u6d88\u606f\u5931\u8d25: ';
  static const _markReadFailedPrefix = '\u6807\u8bb0\u5df2\u8bfb\u5931\u8d25: ';
  static const _courseRequestFallbackMessage =
      '\u5173\u8054\u7684\u9009\u8bfe\u7533\u8bf7\u5df2\u4e0d\u5b58\u5728\uff0c'
      '\u5df2\u4e3a\u4f60\u663e\u793a\u6d88\u606f\u8be6\u60c5';

  bool _loading = true;
  bool _unreadOnly = false;
  String _messageType = 'ALL';
  List<TeacherMessage> _messages = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool showLoading = true}) async {
    if (showLoading) {
      setState(() => _loading = true);
    }
    try {
      final result = await TeacherService.getTeacherMessages(
        unreadOnly: _unreadOnly,
        type: _messageType,
      );
      if (!mounted) return;
      setState(() => _messages = result);
      // 同步未读基线：用户已看到消息列表，重置通知计数起点
      final unreadCount = result.where((m) => !m.isRead).length;
      NotificationService.syncUnreadCount(unreadCount);
    } catch (e) {
      _showSnackBar('$_loadFailedPrefix$e');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  void _showSnackBar(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  void _markMessageReadLocally(TeacherMessage msg) {
    final index = _messages.indexWhere((item) => item.id == msg.id);
    if (index < 0 || !mounted) {
      return;
    }
    setState(() {
      if (_unreadOnly) {
        _messages.removeAt(index);
      } else {
        _messages[index] = _messages[index].copyWith(isRead: true);
      }
    });
  }

  Future<void> _showMessageDetailDialog(TeacherMessage msg) async {
    if (!mounted) return;
    await showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(msg.subject ?? _messageDetailTitle),
        content: Text(
          (msg.content == null || msg.content!.trim().isEmpty)
              ? _emptyContentText
              : msg.content!,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text(_closeLabel),
          ),
        ],
      ),
    );
  }

  Future<void> _openCourseRequestOrFallback(TeacherMessage msg) async {
    final targetId = msg.businessTargetId;
    if (targetId == null) {
      await _showMessageDetailDialog(msg);
      return;
    }
    try {
      await TeacherService.getCourseRequestDetail(targetId);
      if (!mounted) return;
      await GoRouter.of(context).push('/teacher/course-requests/$targetId');
      if (!mounted) return;
      await _load(showLoading: false);
    } catch (_) {
      _showSnackBar(_courseRequestFallbackMessage);
      await _showMessageDetailDialog(msg);
    }
  }

  Future<void> _onTapMessage(TeacherMessage msg) async {
    if (!msg.isRead) {
      _markMessageReadLocally(msg);
      try {
        await TeacherService.markTeacherMessageRead(msg.id);
      } catch (e) {
        _showSnackBar('$_markReadFailedPrefix$e');
        await _load(showLoading: false);
      }
    }

    if (msg.canJumpToCourseRequest) {
      await _openCourseRequestOrFallback(msg);
      return;
    }

    await _showMessageDetailDialog(msg);
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
              msg.canJumpToCourseRequest ? Icons.approval : Icons.mail,
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
            trailing: msg.canJumpToCourseRequest
                ? const Icon(Icons.chevron_right)
                : null,
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text(_messageCenterTitle)),
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
                : RefreshIndicator(
                    onRefresh: () => _load(showLoading: false),
                    child: _buildMessageList(),
                  ),
          ),
        ],
      ),
      bottomNavigationBar: const TeacherBottomNav(currentIndex: 2),
    );
  }
}
