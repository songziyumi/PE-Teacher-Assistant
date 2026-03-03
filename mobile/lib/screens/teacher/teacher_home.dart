import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../providers/auth_provider.dart';
import '../../models/school_class.dart';
import '../../services/teacher_service.dart';

class TeacherHome extends StatefulWidget {
  const TeacherHome({super.key});

  @override
  State<TeacherHome> createState() => _TeacherHomeState();
}

class _TeacherHomeState extends State<TeacherHome> {
  List<SchoolClass> _classes = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      _classes = await TeacherService.getClasses();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
              Text(user!.schoolName!, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.normal)),
          ],
        ),
        actions: [
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
                    itemCount: _classes.length,
                    itemBuilder: (_, i) => _ClassCard(cls: _classes[i]),
                  ),
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
                  child: Text(cls.displayName,
                      style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: cls.type == '选修课'
                        ? Colors.orange.shade100
                        : Colors.blue.shade100,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(cls.type,
                      style: TextStyle(
                          fontSize: 12,
                          color: cls.type == '选修课' ? Colors.orange.shade800 : Colors.blue.shade800)),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                _ActionButton(
                  icon: Icons.how_to_reg,
                  label: '考勤录入',
                  color: const Color(0xFF27ae60),
                  onTap: () => context.push(
                      '/teacher/attendance/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}'),
                ),
                const SizedBox(width: 8),
                _ActionButton(
                  icon: Icons.fitness_center,
                  label: '体测录入',
                  color: const Color(0xFF4a90e2),
                  onTap: () => context.push(
                      '/teacher/physical/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}'),
                ),
                const SizedBox(width: 8),
                _ActionButton(
                  icon: Icons.grade,
                  label: '成绩录入',
                  color: const Color(0xFF9b59b6),
                  onTap: () => context.push(
                      '/teacher/grade/${cls.id}?name=${Uri.encodeComponent(cls.displayName)}'),
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

  const _ActionButton({required this.icon, required this.label, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: color.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: color.withOpacity(0.3)),
          ),
          child: Column(
            children: [
              Icon(icon, color: color, size: 22),
              const SizedBox(height: 4),
              Text(label, style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w500)),
            ],
          ),
        ),
      ),
    );
  }
}
