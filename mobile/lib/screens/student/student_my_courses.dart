import 'package:flutter/material.dart';
import '../../models/student_selection.dart';
import '../../services/student_service.dart';
import '../../widgets/student_bottom_nav.dart';

class StudentMyCoursesScreen extends StatefulWidget {
  const StudentMyCoursesScreen({super.key});

  @override
  State<StudentMyCoursesScreen> createState() => _StudentMyCoursesScreenState();
}

class _StudentMyCoursesScreenState extends State<StudentMyCoursesScreen> {
  bool _loading = true;
  List<StudentSelection> _selections = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final result = await StudentService.getMySelections();
      if (!mounted) return;
      setState(() => _selections = result);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'CONFIRMED':
        return Colors.green;
      case 'PENDING':
        return Colors.orange;
      case 'CANCELLED':
        return Colors.grey;
      default:
        return Colors.blueGrey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('我的选课')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _selections.isEmpty
              ? RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    children: const [
                      SizedBox(height: 200),
                      Center(child: Text('暂无选课记录')),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(12),
                    itemCount: _selections.length,
                    itemBuilder: (_, i) {
                      final item = _selections[i];
                      return Card(
                        margin: const EdgeInsets.only(bottom: 10),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      item.courseName,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: _statusColor(item.status)
                                          .withValues(alpha: 0.15),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: Text(
                                      item.statusText,
                                      style: TextStyle(
                                        color: _statusColor(item.status),
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text('轮次：${item.roundText}'),
                              Text(
                                '志愿：${item.preference > 0 ? item.preference : '-'}',
                              ),
                              Text('提交时间：${_formatDate(item.selectedAt)}'),
                              Text('确认时间：${_formatDate(item.confirmedAt)}'),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
      bottomNavigationBar: const StudentBottomNav(currentIndex: 1),
    );
  }
}
