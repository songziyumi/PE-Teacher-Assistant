import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../models/course_request.dart';
import '../../services/teacher_service.dart';
import '../../widgets/teacher_bottom_nav.dart';

class CourseRequestCenterScreen extends StatefulWidget {
  const CourseRequestCenterScreen({super.key});

  @override
  State<CourseRequestCenterScreen> createState() =>
      _CourseRequestCenterScreenState();
}

class _CourseRequestCenterScreenState extends State<CourseRequestCenterScreen> {
  static const List<String> _tabs = ['PENDING', 'APPROVED', 'REJECTED'];
  static const Map<String, String> _tabLabels = {
    'PENDING': '待审批',
    'APPROVED': '已通过',
    'REJECTED': '已拒绝',
  };

  String _selectedStatus = 'PENDING';
  bool _loading = true;
  bool _batchMode = false;
  bool _batchSubmitting = false;
  List<CourseRequest> _requests = [];
  final Set<int> _selectedRequestIds = <int>{};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final result =
          await TeacherService.getCourseRequests(status: _selectedStatus);
      if (!mounted) return;
      setState(() {
        _requests = result;
        if (_selectedStatus != 'PENDING') {
          _batchMode = false;
          _selectedRequestIds.clear();
        } else {
          _selectedRequestIds
              .removeWhere((id) => !_requests.any((req) => req.id == id));
        }
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载审批数据失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openDetail(CourseRequest req) async {
    if (_batchMode && _selectedStatus == 'PENDING') {
      _toggleSelected(req.id);
      return;
    }
    final changed = await context.push('/teacher/course-requests/${req.id}');
    if (!mounted) return;
    if (changed == true) {
      _load();
    }
  }

  void _toggleBatchMode() {
    if (_batchSubmitting || _selectedStatus != 'PENDING') return;
    setState(() {
      _batchMode = !_batchMode;
      if (!_batchMode) {
        _selectedRequestIds.clear();
      }
    });
  }

  void _toggleSelected(int id) {
    if (_batchSubmitting || _selectedStatus != 'PENDING') return;
    setState(() {
      if (_selectedRequestIds.contains(id)) {
        _selectedRequestIds.remove(id);
      } else {
        _selectedRequestIds.add(id);
      }
    });
  }

  void _selectAll() {
    if (_batchSubmitting || _selectedStatus != 'PENDING') return;
    setState(() {
      _selectedRequestIds.clear();
      _selectedRequestIds.addAll(_requests.map((e) => e.id));
    });
  }

  void _clearSelection() {
    if (_batchSubmitting) return;
    setState(() => _selectedRequestIds.clear());
  }

  Future<void> _batchHandle(bool approve) async {
    if (_batchSubmitting || _selectedStatus != 'PENDING') return;
    if (_selectedRequestIds.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请先选择至少一条待审批记录')),
      );
      return;
    }

    final remark = await _showBatchRemarkDialog(approve);
    if (!mounted || remark == null) return;

    setState(() => _batchSubmitting = true);
    try {
      final result = await TeacherService.batchHandleCourseRequests(
        _selectedRequestIds.toList(),
        approve: approve,
        remark: remark.isEmpty ? null : remark,
      );
      if (!mounted) return;

      final successCount = _toInt(result['successCount']);
      final failedCount = _toInt(result['failedCount']);
      final failedItems = (result['failedItems'] is List)
          ? (result['failedItems'] as List)
          : const [];
      final failedIds = failedItems
          .map((e) => (e is Map ? e['messageId'] : null)?.toString() ?? '')
          .where((id) => id.isNotEmpty)
          .take(5)
          .toList();
      final failedSuffix =
          failedIds.isEmpty ? '' : '，失败ID：${failedIds.join(', ')}';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '批量${approve ? '同意' : '拒绝'}完成：成功$successCount，失败$failedCount$failedSuffix',
          ),
        ),
      );
      _selectedRequestIds.clear();
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('批量操作失败: $e')));
    } finally {
      if (mounted) setState(() => _batchSubmitting = false);
    }
  }

  Future<String?> _showBatchRemarkDialog(bool approve) async {
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('批量${approve ? '同意' : '拒绝'}'),
        content: TextField(
          controller: controller,
          maxLength: 500,
          maxLines: 3,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            labelText: '统一备注（可选）',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () =>
                Navigator.of(dialogContext).pop(controller.text.trim()),
            child: const Text('确认'),
          ),
        ],
      ),
    );
    controller.dispose();
    return result;
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'APPROVED':
        return Colors.green;
      case 'REJECTED':
        return Colors.red;
      default:
        return Colors.orange;
    }
  }

  int _toInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  @override
  Widget build(BuildContext context) {
    final isPendingTab = _selectedStatus == 'PENDING';
    final isAllSelected =
        _requests.isNotEmpty && _selectedRequestIds.length == _requests.length;
    return Scaffold(
      appBar: AppBar(
        title: const Text('选课审批中心'),
        actions: [
          if (isPendingTab)
            TextButton(
              onPressed: _batchSubmitting ? null : _toggleBatchMode,
              child: Text(_batchMode ? '完成' : '批量'),
            ),
        ],
      ),
      body: Column(
        children: [
          const SizedBox(height: 12),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: _tabs.map((status) {
                final selected = _selectedStatus == status;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(_tabLabels[status] ?? status),
                    selected: selected,
                    onSelected: (_) {
                      if (_selectedStatus != status) {
                        setState(() {
                          _selectedStatus = status;
                          if (status != 'PENDING') {
                            _batchMode = false;
                            _selectedRequestIds.clear();
                          }
                        });
                        _load();
                      }
                    },
                  ),
                );
              }).toList(),
            ),
          ),
          if (isPendingTab && _batchMode) ...[
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.blue.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text('已选 ${_selectedRequestIds.length}/${_requests.length}'),
                    OutlinedButton(
                      onPressed:
                          _batchSubmitting ? null : (isAllSelected ? _clearSelection : _selectAll),
                      child: Text(isAllSelected ? '取消全选' : '全选'),
                    ),
                    OutlinedButton(
                      onPressed: _batchSubmitting ? null : () => _batchHandle(false),
                      style:
                          OutlinedButton.styleFrom(foregroundColor: Colors.red),
                      child: const Text('批量拒绝'),
                    ),
                    ElevatedButton(
                      onPressed: _batchSubmitting ? null : () => _batchHandle(true),
                      child: Text(_batchSubmitting ? '处理中...' : '批量同意'),
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 8),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _requests.isEmpty
                    ? const Center(child: Text('暂无数据'))
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.builder(
                          padding: const EdgeInsets.all(12),
                          itemCount: _requests.length,
                          itemBuilder: (_, i) {
                            final req = _requests[i];
                            return Card(
                              margin: const EdgeInsets.only(bottom: 10),
                              child: ListTile(
                                onTap: () => _openDetail(req),
                                leading: (_batchMode && isPendingTab)
                                    ? Checkbox(
                                        value:
                                            _selectedRequestIds.contains(req.id),
                                        onChanged: _batchSubmitting
                                            ? null
                                            : (_) => _toggleSelected(req.id),
                                      )
                                    : null,
                                contentPadding: const EdgeInsets.all(12),
                                title: Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        req.senderName ?? '未知学生',
                                        style: const TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 8, vertical: 3),
                                      decoration: BoxDecoration(
                                        color: _statusColor(req.status)
                                            .withValues(alpha: 0.12),
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Text(
                                        req.statusLabel,
                                        style: TextStyle(
                                          color: _statusColor(req.status),
                                          fontSize: 12,
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                subtitle: Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text('课程：${req.relatedCourseName ?? '-'}'),
                                      const SizedBox(height: 4),
                                      Text('主题：${req.subject ?? '-'}'),
                                      const SizedBox(height: 4),
                                      Text('申请时间：${_formatDate(req.sentAt)}'),
                                    ],
                                  ),
                                ),
                                trailing: (_batchMode && isPendingTab)
                                    ? null
                                    : const Icon(Icons.chevron_right),
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
      bottomNavigationBar: const TeacherBottomNav(currentIndex: 1),
    );
  }
}
