import 'package:flutter/material.dart';

import '../../models/student_message.dart';
import '../../models/student_message_recipient.dart';
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
  static const _title = '消息中心';
  static const _allLabel = '全部';
  static const _unreadOnlyLabel = '仅未读';
  static const _emptyListText = '暂无消息';
  static const _untitledText = '（无主题）';
  static const _detailTitle = '消息详情';
  static const _emptyContentText = '暂无内容';
  static const _closeLabel = '关闭';
  static const _composeLabel = '发消息';
  static const _recipientLabel = '收件教师';
  static const _recipientHint = '请选择教师';
  static const _subjectLabel = '主题';
  static const _subjectHint = '请输入消息主题';
  static const _contentLabel = '内容';
  static const _contentHint = '请输入消息内容';
  static const _cancelLabel = '取消';
  static const _sendLabel = '发送';
  static const _sendingLabel = '发送中...';
  static const _loadFailedPrefix = '加载失败: ';
  static const _sendFailedPrefix = '发送失败: ';
  static const _markReadFailedPrefix = '标记已读失败: ';
  static const _sendSuccessText = '消息已发送';
  static const _noRecipientsText = '暂无可联系教师';
  static const _recipientRequiredText = '请选择收件教师';
  static const _subjectRequiredText = '消息主题不能为空';
  static const _contentRequiredText = '消息内容不能为空';

  bool _loading = true;
  bool _unreadOnly = false;
  List<StudentMessage> _messages = [];
  List<StudentMessageRecipient> _recipients = [];

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
      final recipients = await StudentService.getMessageRecipients();
      if (!mounted) return;
      setState(() {
        _messages = messages;
        _recipients = recipients;
      });
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
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(msg.subject?.isNotEmpty == true ? msg.subject! : _detailTitle),
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

    if (mounted) {
      _load();
    }
  }

  Future<void> _openComposeDialog() async {
    if (_recipients.isEmpty) {
      _showSnackBar(_noRecipientsText);
      return;
    }

    int? selectedTeacherId = _recipients.first.id;
    final subjectController = TextEditingController();
    final contentController = TextEditingController();
    bool sending = false;

    final sent = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          title: const Text(_composeLabel),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                DropdownButtonFormField<int>(
                  value: selectedTeacherId,
                  decoration: const InputDecoration(
                    labelText: _recipientLabel,
                    border: OutlineInputBorder(),
                  ),
                  items: _recipients
                      .map(
                        (recipient) => DropdownMenuItem<int>(
                          value: recipient.id,
                          child: Text(recipient.displayName),
                        ),
                      )
                      .toList(),
                  onChanged: sending
                      ? null
                      : (value) {
                          setDialogState(() => selectedTeacherId = value);
                        },
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: subjectController,
                  enabled: !sending,
                  decoration: const InputDecoration(
                    labelText: _subjectLabel,
                    hintText: _subjectHint,
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: contentController,
                  enabled: !sending,
                  maxLines: 5,
                  decoration: const InputDecoration(
                    labelText: _contentLabel,
                    hintText: _contentHint,
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: sending
                  ? null
                  : () => Navigator.of(dialogContext).pop(false),
              child: const Text(_cancelLabel),
            ),
            FilledButton(
              onPressed: sending
                  ? null
                  : () async {
                      final subject = subjectController.text.trim();
                      final content = contentController.text.trim();
                      if (selectedTeacherId == null) {
                        _showSnackBar(_recipientRequiredText);
                        return;
                      }
                      if (subject.isEmpty) {
                        _showSnackBar(_subjectRequiredText);
                        return;
                      }
                      if (content.isEmpty) {
                        _showSnackBar(_contentRequiredText);
                        return;
                      }

                      setDialogState(() => sending = true);
                      try {
                        await StudentService.sendStudentMessage(
                          teacherId: selectedTeacherId!,
                          subject: subject,
                          content: content,
                        );
                        if (dialogContext.mounted) {
                          Navigator.of(dialogContext).pop(true);
                        }
                      } catch (e) {
                        if (dialogContext.mounted) {
                          setDialogState(() => sending = false);
                        }
                        _showSnackBar('$_sendFailedPrefix$e');
                      }
                    },
              child: Text(sending ? _sendingLabel : _sendLabel),
            ),
          ],
        ),
      ),
    );

    subjectController.dispose();
    contentController.dispose();

    if (sent == true) {
      _showSnackBar(_sendSuccessText);
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
        onPressed: _loading ? null : _openComposeDialog,
        icon: const Icon(Icons.edit_outlined),
        label: const Text(_composeLabel),
      ),
      bottomNavigationBar: const StudentBottomNav(currentIndex: 2),
    );
  }
}
