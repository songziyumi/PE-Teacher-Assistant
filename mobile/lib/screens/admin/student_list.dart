import 'package:flutter/material.dart';
import '../../services/admin_service.dart';
import '../../models/student.dart';
import '../../models/school_class.dart';

class StudentListScreen extends StatefulWidget {
  const StudentListScreen({super.key});

  @override
  State<StudentListScreen> createState() => _StudentListScreenState();
}

class _StudentListScreenState extends State<StudentListScreen> {
  final _searchCtrl = TextEditingController();
  List<Student> _students = [];
  int _page = 0;
  int _totalPages = 0;
  bool _loading = false;
  List<SchoolClass> _classes = [];
  int? _selectedClassId;

  @override
  void initState() {
    super.initState();
    _loadClasses();
    _load();
  }

  Future<void> _loadClasses() async {
    try {
      _classes = await AdminService.getClasses();
      if (mounted) setState(() {});
    } catch (_) {}
  }

  Future<void> _load({int page = 0}) async {
    setState(() => _loading = true);
    try {
      final data = await AdminService.getStudents(
        keyword: _searchCtrl.text,
        classId: _selectedClassId,
        page: page,
      );
      final content = (data['content'] as List)
          .map((e) => Student.fromJson(e as Map<String, dynamic>))
          .toList();
      setState(() {
        _students = content;
        _page = data['page'] as int;
        _totalPages = data['totalPages'] as int;
      });
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('学生管理')),
      body: Column(
        children: [
          // 搜索栏
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchCtrl,
                    decoration: const InputDecoration(
                      hintText: '搜索姓名/学号',
                      prefixIcon: Icon(Icons.search),
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                    onSubmitted: (_) => _load(),
                  ),
                ),
                const SizedBox(width: 8),
                DropdownButton<int?>(
                  value: _selectedClassId,
                  hint: const Text('全部班级'),
                  items: [
                    const DropdownMenuItem(value: null, child: Text('全部班级')),
                    ..._classes.map((c) =>
                        DropdownMenuItem(value: c.id, child: Text(c.displayName))),
                  ],
                  onChanged: (v) {
                    setState(() => _selectedClassId = v);
                    _load();
                  },
                ),
                const SizedBox(width: 8),
                ElevatedButton(onPressed: () => _load(), child: const Text('搜索')),
              ],
            ),
          ),
          // 列表
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _students.isEmpty
                    ? const Center(child: Text('暂无数据'))
                    : ListView.builder(
                        itemCount: _students.length,
                        itemBuilder: (_, i) {
                          final s = _students[i];
                          return ListTile(
                            leading: CircleAvatar(
                              backgroundColor: s.isMale
                                  ? Colors.blue.shade100
                                  : Colors.pink.shade100,
                              child: Text(s.gender ?? '?',
                                  style: TextStyle(
                                      color: s.isMale ? Colors.blue : Colors.pink,
                                      fontWeight: FontWeight.bold)),
                            ),
                            title: Text(s.name),
                            subtitle: Text('${s.studentNo ?? ''} · ${s.className ?? ''}'),
                          );
                        },
                      ),
          ),
          // 分页
          if (_totalPages > 1)
            Padding(
              padding: const EdgeInsets.all(8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton(
                    icon: const Icon(Icons.chevron_left),
                    onPressed: _page > 0 ? () => _load(page: _page - 1) : null,
                  ),
                  Text('${_page + 1} / $_totalPages'),
                  IconButton(
                    icon: const Icon(Icons.chevron_right),
                    onPressed: _page < _totalPages - 1 ? () => _load(page: _page + 1) : null,
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
