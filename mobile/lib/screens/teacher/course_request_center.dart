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
  List<CourseRequest> _requests = [];

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
      setState(() => _requests = result);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载审批数据失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openDetail(CourseRequest req) async {
    final changed = await context.push('/teacher/course-requests/${req.id}');
    if (!mounted) return;
    if (changed == true) {
      _load();
    }
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('选课审批中心')),
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
                      setState(() => _selectedStatus = status);
                      _load();
                    },
                  ),
                );
              }).toList(),
            ),
          ),
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
                                            .withOpacity(0.12),
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
                                trailing: const Icon(Icons.chevron_right),
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
