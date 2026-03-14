import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../models/school_class.dart';
import '../../providers/auth_provider.dart';
import '../../services/teacher_service.dart';
import '../../widgets/teacher_bottom_nav.dart';

class TeacherHome extends StatefulWidget {
  const TeacherHome({super.key});

  @override
  State<TeacherHome> createState() => _TeacherHomeState();
}

class _TeacherHomeState extends State<TeacherHome> {
  List<SchoolClass> _classes = [];
  int _pendingRequestCount = 0;
  int _unreadMessageCount = 0;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final results = await Future.wait([
        TeacherService.getClasses(),
        TeacherService.getCourseRequestSummary(),
        TeacherService.getUnreadMessageCount(),
      ]);
      _classes = results[0] as List<SchoolClass>;
      final summary = results[1] as Map<String, int>;
      _pendingRequestCount = summary['pending'] ?? 0;
      _unreadMessageCount = results[2] as int;
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openRequestCenter() async {
    await context.push('/teacher/course-requests');
    if (mounted) _load();
  }

  Future<void> _openMessageCenter() async {
    await context.push('/teacher/messages');
    if (mounted) _load();
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().user;
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('我的班级'),
            if (user?.schoolName != null)
              Text(
                user!.schoolName!,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.normal,
                ),
              ),
          ],
        ),
        actions: [
          IconButton(
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(Icons.approval),
                if (_pendingRequestCount > 0)
                  Positioned(
                    right: -6,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 5,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      constraints:
                          const BoxConstraints(minWidth: 16, minHeight: 14),
                      child: Text(
                        _pendingRequestCount > 99
                            ? '99+'
                            : '$_pendingRequestCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
            onPressed: _openRequestCenter,
          ),
          IconButton(
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(Icons.mail),
                if (_unreadMessageCount > 0)
                  Positioned(
                    right: -6,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 5,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      constraints:
                          const BoxConstraints(minWidth: 16, minHeight: 14),
                      child: Text(
                        _unreadMessageCount > 99
                            ? '99+'
                            : '$_unreadMessageCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
            onPressed: _openMessageCenter,
          ),
          IconButton(
            icon: const Icon(Icons.person),
            onPressed: () => context.push('/teacher/profile'),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<AuthProvider>().logout(),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _classes.isEmpty
              ? const Center(child: Text('暂无班级'))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: _classes.length + 3,
                    itemBuilder: (_, i) {
                      if (i == 0) {
                        return _WelcomeCard(name: user?.name ?? '');
                      }
                      if (i == 1) {
                        return _RequestEntryCard(
                          pendingCount: _pendingRequestCount,
                          unreadCount: _unreadMessageCount,
                          onTap: _openRequestCenter,
                        );
                      }
                      if (i == 2) {
                        return _ExportEntryCard(
                          onTap: () =>
                              context.push('/teacher/attendance-export'),
                        );
                      }
                      return _ClassCard(cls: _classes[i - 3]);
                    },
                  ),
                ),
      bottomNavigationBar: const TeacherBottomNav(currentIndex: 0),
    );
  }
}

class _WelcomeCard extends StatelessWidget {
  final String name;

  const _WelcomeCard({required this.name});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: Colors.blue.shade50,
      child: ListTile(
        leading: const Icon(Icons.waving_hand, color: Colors.blue),
        title: Text(
          '欢迎${name.isEmpty ? '' : name}老师',
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: const Text('祝您今天教学顺利'),
      ),
    );
  }
}

class _RequestEntryCard extends StatelessWidget {
  final int pendingCount;
  final int unreadCount;
  final VoidCallback onTap;

  const _RequestEntryCard({
    required this.pendingCount,
    required this.unreadCount,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: Colors.orange.shade50,
      child: ListTile(
        leading: const Icon(Icons.approval, color: Colors.orange),
        title:
            const Text('选课审批中心', style: TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text('待审批：$pendingCount    未读消息：$unreadCount'),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

class _ExportEntryCard extends StatelessWidget {
  final VoidCallback onTap;

  const _ExportEntryCard({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: Colors.teal.shade50,
      child: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.download, color: Colors.teal),
            title: const Text('导出考勤记录',
                style: TextStyle(fontWeight: FontWeight.bold)),
            subtitle: const Text('按班级和日期范围导出 Excel'),
            trailing: const Icon(Icons.chevron_right),
            onTap: onTap,
          ),
          ListTile(
            leading: const Icon(Icons.folder_zip, color: Colors.teal),
            title: const Text('数据导出',
                style: TextStyle(fontWeight: FontWeight.bold)),
            subtitle: const Text('导出审批记录、学生名单'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/teacher/data-export'),
          ),
        ],
      ),
    );
  }
}

class _ClassCard extends StatelessWidget {
  final SchoolClass cls;
  const _ClassCard({required this.cls});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    cls.displayName,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: cls.type == '选修课'
                        ? Colors.orange.shade100
                        : Colors.blue.shade100,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    cls.type,
                    style: TextStyle(
                      fontSize: 12,
                      color: cls.type == '选修课'
                          ? Colors.orange.shade800
                          : Colors.blue.shade800,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                _ActionButton(
                  icon: Icons.people,
                  label: '学生列表',
                  color: const Color(0xFF8e44ad),
                  onTap: () => context.push(
                    '/teacher/students/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}',
                  ),
                ),
                const SizedBox(width: 8),
                _ActionButton(
                  icon: Icons.how_to_reg,
                  label: '考勤录入',
                  color: const Color(0xFF27ae60),
                  onTap: () => context.push(
                    '/teacher/attendance/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}',
                  ),
                ),
                const SizedBox(width: 8),
                _ActionButton(
                  icon: Icons.fitness_center,
                  label: '体测录入',
                  color: const Color(0xFF4a90e2),
                  onTap: () => context.push(
                    '/teacher/physical/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}',
                  ),
                ),
                const SizedBox(width: 8),
                _ActionButton(
                  icon: Icons.grade,
                  label: '成绩录入',
                  color: const Color(0xFF9b59b6),
                  onTap: () => context.push(
                    '/teacher/grade/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}',
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ActionButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: color.withValues(alpha: 0.3)),
          ),
          child: Column(
            children: [
              Icon(icon, color: color, size: 22),
              const SizedBox(height: 4),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  color: color,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
