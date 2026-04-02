import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../models/student_message_recipient.dart';
import '../../services/student_service.dart';

class StudentMessageComposeScreen extends StatefulWidget {
  const StudentMessageComposeScreen({
    super.key,
    this.initialTeacherId,
    this.initialSubject = '',
    this.initialContent = '',
  });

  final int? initialTeacherId;
  final String initialSubject;
  final String initialContent;

  @override
  State<StudentMessageComposeScreen> createState() =>
      _StudentMessageComposeScreenState();
}

class _StudentMessageComposeScreenState
    extends State<StudentMessageComposeScreen> {
  static const _title = '发送消息';
  static const _recipientLabel = '收件老师';
  static const _subjectLabel = '主题';
  static const _subjectHint = '请输入消息主题';
  static const _contentLabel = '内容';
  static const _contentHint = '请输入想对老师说的话';
  static const _subjectCounterHint = '最多 200 字';
  static const _contentCounterHint = '建议 1000 字内';
  static const _sendLabel = '发送';
  static const _sendingLabel = '发送中...';
  static const _loadFailedPrefix = '加载失败：';
  static const _sendFailedPrefix = '发送失败：';
  static const _noRecipientsText = '暂无可联系老师';
  static const _recipientRequiredText = '请选择收件老师';
  static const _subjectRequiredText = '消息主题不能为空';
  static const _contentRequiredText = '消息内容不能为空';
  static const _discardDraftTitle = '放弃当前草稿？';
  static const _discardDraftContent = '你已填写部分内容，离开后将不会保留。';
  static const _discardLabel = '放弃';
  static const _continueEditLabel = '继续编辑';
  static const _retryLabel = '重试';
  static const _recentSuffix = '（最近）';
  static const _recentTeacherPrefKey = 'student_message_recent_teacher_ids';
  static const _draftPrefKey = 'student_message_compose_draft';
  static const _subjectMaxLength = 200;
  static const _maxRecentTeachers = 5;
  static const _restoredDraftText = '已恢复上次未发送的草稿';
  static const _clearDraftLabel = '清空草稿';
  static const _clearDraftTitle = '清空当前草稿？';
  static const _clearDraftContent = '清空后将恢复到初始内容，且无法撤销。';
  static const _keepDraftLabel = '保留';
  static const _restoredDraftPrefix = '恢复时间：';

  final _subjectController = TextEditingController();
  final _contentController = TextEditingController();
  final _subjectFocusNode = FocusNode();
  final _contentFocusNode = FocusNode();
  final _subjectFieldKey = GlobalKey();
  final _contentFieldKey = GlobalKey();

  Timer? _draftSaveTimer;
  bool _loading = true;
  bool _sending = false;
  bool _forcePop = false;
  bool _hydratingDraft = false;
  bool _restoredDraft = false;
  String? _loadError;
  DateTime? _restoredDraftAt;
  List<StudentMessageRecipient> _recipients = [];
  List<int> _recentTeacherIds = [];
  int? _selectedTeacherId;
  int? _initialResolvedTeacherId;

  @override
  void initState() {
    super.initState();
    _subjectController.text = widget.initialSubject;
    _contentController.text = widget.initialContent;
    _subjectController.addListener(_onDraftChanged);
    _contentController.addListener(_onDraftChanged);
    _subjectFocusNode.addListener(_handleSubjectFocusChange);
    _contentFocusNode.addListener(_handleContentFocusChange);
    _loadRecipients();
  }

  @override
  void dispose() {
    _draftSaveTimer?.cancel();
    _persistDraft();
    _subjectController.removeListener(_onDraftChanged);
    _contentController.removeListener(_onDraftChanged);
    _subjectFocusNode.removeListener(_handleSubjectFocusChange);
    _contentFocusNode.removeListener(_handleContentFocusChange);
    _subjectController.dispose();
    _contentController.dispose();
    _subjectFocusNode.dispose();
    _contentFocusNode.dispose();
    super.dispose();
  }

  void _onDraftChanged() {
    _scheduleDraftSave();
    if (mounted) {
      setState(() {});
    }
  }

  String get _draftContextKey =>
      '${widget.initialTeacherId ?? ''}|${widget.initialSubject.trim()}|${widget.initialContent.trim()}';

  Future<List<int>> _loadRecentTeacherIds() async {
    final prefs = await SharedPreferences.getInstance();
    return (prefs.getStringList(_recentTeacherPrefKey) ?? const [])
        .map(int.tryParse)
        .whereType<int>()
        .toList();
  }

  Future<void> _saveRecentTeacherId(int teacherId) async {
    final prefs = await SharedPreferences.getInstance();
    final recentIds = await _loadRecentTeacherIds();
    final updated = [teacherId, ...recentIds.where((id) => id != teacherId)]
        .take(_maxRecentTeachers)
        .map((id) => id.toString())
        .toList();
    await prefs.setStringList(_recentTeacherPrefKey, updated);
  }

  Future<Map<String, dynamic>?> _loadDraftSnapshot() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_draftPrefKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      return Map<String, dynamic>.from(jsonDecode(raw) as Map);
    } catch (_) {
      await prefs.remove(_draftPrefKey);
      return null;
    }
  }

  Future<void> _clearDraft() async {
    _draftSaveTimer?.cancel();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_draftPrefKey);
  }

  void _scheduleDraftSave() {
    if (_hydratingDraft || _loading || _sending) return;
    _draftSaveTimer?.cancel();
    _draftSaveTimer = Timer(
      const Duration(milliseconds: 500),
      _persistDraft,
    );
  }

  Future<void> _persistDraft() async {
    if (_hydratingDraft) return;
    final prefs = await SharedPreferences.getInstance();
    if (!_hasDraft) {
      await prefs.remove(_draftPrefKey);
      return;
    }

    await prefs.setString(
      _draftPrefKey,
      jsonEncode({
        'contextKey': _draftContextKey,
        'teacherId': _selectedTeacherId,
        'subject': _subjectController.text,
        'content': _contentController.text,
        'updatedAt': DateTime.now().toIso8601String(),
      }),
    );
  }

  List<StudentMessageRecipient> _sortRecipientsByRecent(
    List<StudentMessageRecipient> recipients,
    List<int> recentTeacherIds,
  ) {
    if (recentTeacherIds.isEmpty) return recipients;

    final recipientById = {
      for (final recipient in recipients) recipient.id: recipient,
    };
    final recentRecipients = recentTeacherIds
        .map((id) => recipientById[id])
        .whereType<StudentMessageRecipient>()
        .toList();
    final recentIdSet = recentRecipients.map((item) => item.id).toSet();
    final others = recipients
        .where((recipient) => !recentIdSet.contains(recipient.id))
        .toList();
    return [...recentRecipients, ...others];
  }

  void _handleContentFocusChange() {
    if (!_contentFocusNode.hasFocus) return;
    if (mounted) setState(() {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final currentContext = _contentFieldKey.currentContext;
      if (currentContext == null || !mounted) return;
      Scrollable.ensureVisible(
        currentContext,
        alignment: 1,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    });
  }

  void _handleSubjectFocusChange() {
    if (mounted) setState(() {});
    if (!_subjectFocusNode.hasFocus) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final currentContext = _subjectFieldKey.currentContext;
      if (currentContext == null || !mounted) return;
      Scrollable.ensureVisible(
        currentContext,
        alignment: 1,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    });
  }

  Future<void> _loadRecipients() async {
    setState(() {
      _loading = true;
      _loadError = null;
    });
    try {
      final recentTeacherIds = await _loadRecentTeacherIds();
      final draftSnapshot = await _loadDraftSnapshot();
      final recipients = await StudentService.getMessageRecipients();
      if (!mounted) return;

      final orderedRecipients =
          _sortRecipientsByRecent(recipients, recentTeacherIds);
      final preferredTeacherId = widget.initialTeacherId;
      final hasPreferredTeacher = preferredTeacherId != null &&
          orderedRecipients.any((recipient) => recipient.id == preferredTeacherId);
      final resolvedTeacherId = orderedRecipients.isEmpty
          ? null
          : (hasPreferredTeacher ? preferredTeacherId : orderedRecipients.first.id);
      final canRestoreDraft = draftSnapshot?['contextKey'] == _draftContextKey;
      final restoredTeacherId = canRestoreDraft
          ? _toNullableInt(draftSnapshot?['teacherId'])
          : null;
      final hasRestoredTeacher = restoredTeacherId != null &&
          orderedRecipients.any((recipient) => recipient.id == restoredTeacherId);
      final selectedTeacherId = hasRestoredTeacher
          ? restoredTeacherId
          : resolvedTeacherId;
      final restoredSubject = canRestoreDraft
          ? (draftSnapshot?['subject']?.toString() ?? widget.initialSubject)
          : widget.initialSubject;
      final restoredContent = canRestoreDraft
          ? (draftSnapshot?['content']?.toString() ?? widget.initialContent)
          : widget.initialContent;
      final restoredDraftAt = canRestoreDraft
          ? _toNullableDateTime(draftSnapshot?['updatedAt'])
          : null;

      _hydratingDraft = true;
      _subjectController.text = restoredSubject;
      _contentController.text = restoredContent;
      _hydratingDraft = false;

      setState(() {
        _recentTeacherIds = recentTeacherIds;
        _recipients = orderedRecipients;
        _selectedTeacherId = selectedTeacherId;
        _initialResolvedTeacherId = resolvedTeacherId;
        _restoredDraft = canRestoreDraft;
        _restoredDraftAt = restoredDraftAt;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _loadError = '$_loadFailedPrefix$e');
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

  bool get _hasDraft {
    final currentSubject = _subjectController.text.trim();
    final currentContent = _contentController.text.trim();
    return currentSubject != widget.initialSubject.trim() ||
        currentContent != widget.initialContent.trim() ||
        _selectedTeacherId != _initialResolvedTeacherId;
  }

  int? _toNullableInt(dynamic value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value.toString());
  }

  DateTime? _toNullableDateTime(dynamic value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString());
  }

  String _formatDateTime(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  Future<bool> _confirmDiscardIfNeeded() async {
    if (_sending) return false;
    if (!_hasDraft) return true;

    final discard = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text(_discardDraftTitle),
        content: const Text(_discardDraftContent),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text(_continueEditLabel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text(_discardLabel),
          ),
        ],
      ),
    );
    return discard == true;
  }

  Future<void> _resetToInitialDraftState() async {
    await _clearDraft();
    if (!mounted) return;

    _hydratingDraft = true;
    _subjectController.text = widget.initialSubject;
    _contentController.text = widget.initialContent;
    _hydratingDraft = false;

    setState(() {
      _selectedTeacherId = _initialResolvedTeacherId;
      _restoredDraft = false;
      _restoredDraftAt = null;
    });
  }

  Future<void> _clearCurrentDraft() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text(_clearDraftTitle),
        content: const Text(_clearDraftContent),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text(_keepDraftLabel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text(_clearDraftLabel),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await _resetToInitialDraftState();
    }
  }

  Future<void> _handlePopAttempt() async {
    if (_forcePop) return;
    final shouldPop = await _confirmDiscardIfNeeded();
    if (!mounted || !shouldPop) return;
    await _clearDraft();
    if (!mounted) return;
    setState(() => _forcePop = true);
    Navigator.of(context).pop();
  }

  Future<void> _submit() async {
    final teacherId = _selectedTeacherId;
    final subject = _subjectController.text.trim();
    final content = _contentController.text.trim();
    if (teacherId == null) {
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

    setState(() => _sending = true);
    try {
      await StudentService.sendStudentMessage(
        teacherId: teacherId,
        subject: subject,
        content: content,
      );
      await _saveRecentTeacherId(teacherId);
      await _clearDraft();
      if (!mounted) return;
      setState(() {
        _forcePop = true;
        _restoredDraft = false;
        _restoredDraftAt = null;
      });
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      _showSnackBar('$_sendFailedPrefix$e');
      setState(() => _sending = false);
    }
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_loadError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_loadError!, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: _loadRecipients,
                child: const Text(_retryLabel),
              ),
            ],
          ),
        ),
      );
    }
    if (_recipients.isEmpty) {
      return const Center(child: Text(_noRecipientsText));
    }

    final recentIdSet = _recentTeacherIds.toSet();
    final keyboardInset = MediaQuery.of(context).viewInsets.bottom;
    final editingSubject = _subjectFocusNode.hasFocus;
    return Column(
      children: [
        Expanded(
          child: ListView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
            children: [
              if (_restoredDraft)
                Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  color: Colors.amber.shade50,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Padding(
                          padding: EdgeInsets.only(top: 2),
                          child: Icon(Icons.restore_outlined, color: Colors.orange),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                _restoredDraftText,
                                style: TextStyle(fontWeight: FontWeight.w600),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '$_restoredDraftPrefix${_formatDateTime(_restoredDraftAt)}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ),
                        ),
                        TextButton(
                          onPressed: _sending ? null : _clearCurrentDraft,
                          child: const Text(_clearDraftLabel),
                        ),
                      ],
                    ),
                  ),
                ),
              DropdownButtonFormField<int>(
                initialValue: _selectedTeacherId,
                isExpanded: true,
                decoration: const InputDecoration(
                  labelText: _recipientLabel,
                  border: OutlineInputBorder(),
                ),
                items: _recipients
                    .map(
                      (recipient) => DropdownMenuItem<int>(
                        value: recipient.id,
                        child: Text(
                          recentIdSet.contains(recipient.id)
                              ? '${recipient.displayName}$_recentSuffix'
                              : recipient.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: _sending
                    ? null
                    : (value) {
                        setState(() => _selectedTeacherId = value);
                        _scheduleDraftSave();
                      },
              ),
              const SizedBox(height: 12),
              TextField(
                key: _subjectFieldKey,
                controller: _subjectController,
                focusNode: _subjectFocusNode,
                enabled: !_sending,
                textInputAction: TextInputAction.next,
                scrollPadding: EdgeInsets.only(bottom: keyboardInset + 24),
                maxLength: _subjectMaxLength,
                decoration: const InputDecoration(
                  labelText: _subjectLabel,
                  hintText: _subjectHint,
                  helperText: _subjectCounterHint,
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 180),
          switchInCurve: Curves.easeOut,
          switchOutCurve: Curves.easeIn,
          child: editingSubject
               ? const SizedBox.shrink()
               : AnimatedPadding(
                   key: const ValueKey('content-panel'),
                   duration: const Duration(milliseconds: 220),
                   curve: Curves.easeOut,
                   padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                   child: SafeArea(
                     top: false,
                     child: Material(
                      elevation: 6,
                      color: Theme.of(context).colorScheme.surface,
                      borderRadius: BorderRadius.circular(16),
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              children: [
                                Text(
                                  _contentLabel,
                                  style: Theme.of(context).textTheme.titleSmall,
                                ),
                                const Spacer(),
                                Text(
                                  '${_contentController.text.length} / $_contentCounterHint',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              key: _contentFieldKey,
                              controller: _contentController,
                              focusNode: _contentFocusNode,
                              enabled: !_sending,
                              keyboardType: TextInputType.multiline,
                              textInputAction: TextInputAction.newline,
                              minLines: 4,
                              maxLines: 8,
                              textAlignVertical: TextAlignVertical.top,
                              scrollPadding: EdgeInsets.only(
                                bottom: keyboardInset + 24,
                              ),
                              decoration: const InputDecoration(
                                hintText: _contentHint,
                                border: InputBorder.none,
                                contentPadding: EdgeInsets.zero,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _forcePop || (!_hasDraft && !_sending),
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        _handlePopAttempt();
      },
      child: Scaffold(
        resizeToAvoidBottomInset: true,
        appBar: AppBar(
          title: const Text(_title),
          actions: [
            if (_hasDraft)
              IconButton(
                onPressed: _sending ? null : _clearCurrentDraft,
                tooltip: _clearDraftLabel,
                icon: const Icon(Icons.delete_outline),
              ),
            TextButton(
              onPressed: (_loading || _sending || _recipients.isEmpty)
                  ? null
                  : _submit,
              child: Text(_sending ? _sendingLabel : _sendLabel),
            ),
          ],
        ),
        body: _buildBody(),
      ),
    );
  }
}
