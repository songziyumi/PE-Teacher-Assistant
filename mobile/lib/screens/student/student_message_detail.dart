import 'package:flutter/material.dart';

import '../../models/student_message.dart';
import 'student_message_compose.dart';

class StudentMessageDetailScreen extends StatelessWidget {
  const StudentMessageDetailScreen({
    super.key,
    required this.message,
  });

  static const _detailTitle = '消息详情';
  static const _untitledText = '（无主题）';
  static const _emptyContentText = '暂无内容';
  static const _senderPrefix = '发件人：';
  static const _timePrefix = '发送时间：';
  static const _replyLabel = '回复老师';

  final StudentMessage message;

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  bool get _canReply {
    final senderType = message.senderType?.trim().toUpperCase();
    return message.senderId != null &&
        message.senderId! > 0 &&
        (senderType == null || senderType.isEmpty || senderType == 'TEACHER');
  }

  String get _replySubject {
    final subject = message.subject?.trim() ?? '';
    if (subject.isEmpty) return '回复';
    if (subject.startsWith('回复：')) return subject;
    return '回复：$subject';
  }

  Future<void> _reply(BuildContext context) async {
    final sent = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => StudentMessageComposeScreen(
          initialTeacherId: message.senderId,
          initialSubject: _replySubject,
        ),
      ),
    );
    if (sent == true && context.mounted) {
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = message.subject?.trim().isNotEmpty == true
        ? message.subject!.trim()
        : _untitledText;
    final content = message.content?.trim().isNotEmpty == true
        ? message.content!.trim()
        : _emptyContentText;
    final sender = message.senderName?.trim().isNotEmpty == true
        ? message.senderName!.trim()
        : '-';

    return Scaffold(
      appBar: AppBar(title: const Text(_detailTitle)),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 12),
              Text(
                '$_senderPrefix$sender',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 4),
              Text(
                '$_timePrefix${_formatDate(message.sentAt)}',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Colors.grey.shade600,
                    ),
              ),
              const SizedBox(height: 16),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  content,
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
              ),
            ],
          ),
        ),
      ),
      bottomNavigationBar: _canReply
          ? SafeArea(
              minimum: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: FilledButton.icon(
                onPressed: () => _reply(context),
                icon: const Icon(Icons.reply_outlined),
                label: const Text(_replyLabel),
              ),
            )
          : null,
    );
  }
}
