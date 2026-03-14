import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../services/teacher_service.dart';

class TeacherOperationTimelineScreen extends StatefulWidget {
  const TeacherOperationTimelineScreen({super.key});

  @override
  State<TeacherOperationTimelineScreen> createState() =>
      _TeacherOperationTimelineScreenState();
}

class _TeacherOperationTimelineScreenState
    extends State<TeacherOperationTimelineScreen> {
  final List<_TimelineEntry> _entries = [];
  int _page = 0;
  bool _loading = false;
  bool _hasMore = true;
  static const _pageSize = 20;

  @override
  void initState() {
    super.initState();
    _loadMore();
  }

  Future<void> _loadMore() async {
    if (_loading || !_hasMore) return;
    setState(() => _loading = true);
    try {
      final data = await TeacherService.getOperationTimeline(
        page: _page,
        size: _pageSize,
      );
      final items = (data['content'] as List)
          .map((e) => _TimelineEntry.fromJson(e as Map<String, dynamic>))
          .toList();
      final totalPages = (data['totalPages'] as num).toInt();
      if (!mounted) return;
      setState(() {
        _entries.addAll(items);
        _page++;
        _hasMore = _page < totalPages;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _refresh() async {
    setState(() {
      _entries.clear();
      _page = 0;
      _hasMore = true;
    });
    await _loadMore();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('操作时间线')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: _entries.isEmpty && !_loading
            ? _buildEmpty()
            : _buildList(),
      ),
    );
  }

  Widget _buildEmpty() {
    return const Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.history, size: 64, color: Colors.grey),
          SizedBox(height: 12),
          Text('暂无操作记录', style: TextStyle(color: Colors.grey)),
        ],
      ),
    );
  }

  Widget _buildList() {
    // 分组：按日期
    final groups = <String, List<_TimelineEntry>>{};
    for (final e in _entries) {
      final key = _dateGroupKey(e.operatedAt);
      groups.putIfAbsent(key, () => []).add(e);
    }
    final groupKeys = groups.keys.toList();

    return ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: groupKeys.length + (_hasMore ? 1 : 0),
      itemBuilder: (ctx, i) {
        if (i == groupKeys.length) {
          // 加载更多触发器
          WidgetsBinding.instance.addPostFrameCallback((_) => _loadMore());
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        final key = groupKeys[i];
        final dayEntries = groups[key]!;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildDateHeader(key),
            ...dayEntries.map(_buildCard),
          ],
        );
      },
    );
  }

  Widget _buildDateHeader(String label) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Colors.grey,
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  Widget _buildCard(_TimelineEntry entry) {
    final cfg = _actionConfig(entry.action);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 竖线 + 圆形图标
          Column(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: cfg.color.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(cfg.icon, size: 18, color: cfg.color),
              ),
            ],
          ),
          const SizedBox(width: 12),
          // 内容
          Expanded(
            child: Card(
              margin: EdgeInsets.zero,
              elevation: 0.5,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            entry.title,
                            style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                            ),
                          ),
                        ),
                        Text(
                          _timeLabel(entry.operatedAt),
                          style: const TextStyle(
                            fontSize: 11,
                            color: Colors.grey,
                          ),
                        ),
                      ],
                    ),
                    if (entry.description != null &&
                        entry.description!.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        entry.description!,
                        style: const TextStyle(
                          fontSize: 13,
                          color: Colors.black87,
                        ),
                      ),
                    ],
                    if (entry.targetCount != null &&
                        entry.targetCount! > 1) ...[
                      const SizedBox(height: 4),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: cfg.color.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '共 ${entry.targetCount} 条',
                          style: TextStyle(
                              fontSize: 11, color: cfg.color),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _dateGroupKey(DateTime dt) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final d = DateTime(dt.year, dt.month, dt.day);
    final diff = today.difference(d).inDays;
    if (diff == 0) return '今天';
    if (diff == 1) return '昨天';
    if (diff < 7) return '$diff天前';
    return DateFormat('M月d日').format(dt);
  }

  String _timeLabel(DateTime dt) {
    return DateFormat('HH:mm').format(dt);
  }

  _ActionConfig _actionConfig(String action) {
    switch (action) {
      case 'ATTENDANCE_SAVE':
        return const _ActionConfig(Icons.how_to_reg, Colors.blue);
      case 'PHYSICAL_TEST_SAVE':
        return const _ActionConfig(Icons.fitness_center, Colors.green);
      case 'TERM_GRADE_SAVE':
        return const _ActionConfig(Icons.grade, Colors.orange);
      case 'STUDENT_UPDATE':
        return const _ActionConfig(Icons.person_outline, Colors.purple);
      case 'STUDENT_BATCH_STATUS':
        return const _ActionConfig(Icons.manage_accounts, Colors.deepPurple);
      case 'STUDENT_BATCH_ELECTIVE':
        return const _ActionConfig(Icons.class_, Colors.indigo);
      case 'APPROVE':
        return const _ActionConfig(Icons.check_circle_outline, Colors.green);
      case 'REJECT':
        return const _ActionConfig(Icons.cancel_outlined, Colors.red);
      case 'BATCH_APPROVE':
        return const _ActionConfig(Icons.done_all, Colors.green);
      case 'BATCH_REJECT':
        return const _ActionConfig(Icons.block, Colors.red);
      default:
        return const _ActionConfig(Icons.history, Colors.grey);
    }
  }
}

class _TimelineEntry {
  final String id;
  final String action;
  final String title;
  final String? description;
  final int? targetCount;
  final DateTime operatedAt;

  _TimelineEntry({
    required this.id,
    required this.action,
    required this.title,
    this.description,
    this.targetCount,
    required this.operatedAt,
  });

  factory _TimelineEntry.fromJson(Map<String, dynamic> json) {
    return _TimelineEntry(
      id: json['id']?.toString() ?? '',
      action: json['action'] as String? ?? '',
      title: json['title'] as String? ?? '',
      description: json['description'] as String?,
      targetCount: (json['targetCount'] as num?)?.toInt(),
      operatedAt: DateTime.parse(json['operatedAt'] as String),
    );
  }
}

class _ActionConfig {
  final IconData icon;
  final Color color;
  const _ActionConfig(this.icon, this.color);
}
