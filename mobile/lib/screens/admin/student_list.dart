import 'package:flutter/material.dart';
import '../../services/admin_service.dart';
import '../../models/student.dart';
import '../../models/school_class.dart';
import '../../models/elective_class.dart';

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
  List<Grade> _grades = [];
  List<ElectiveClass> _electiveClasses = [];
  int? _selectedClassId;

  bool _isElectiveClassType(String type) {
    final normalized = type.trim().replaceAll(' ', '');
    return normalized.contains('选修');
  }

  int _compareSchoolClass(SchoolClass a, SchoolClass b) {
    final gradeCompare = (a.gradeName ?? '').compareTo(b.gradeName ?? '');
    if (gradeCompare != 0) return gradeCompare;
    return a.name.compareTo(b.name);
  }

  @override
  void initState() {
    super.initState();
    _loadMeta();
    _load();
  }

  Future<void> _loadMeta() async {
    try {
      final results = await Future.wait([
        AdminService.getClasses(),
        AdminService.getGrades(),
        AdminService.getElectiveClasses(),
      ]);
      if (mounted)
        setState(() {
          _classes = results[0] as List<SchoolClass>;
          _grades = results[1] as List<Grade>;
          _electiveClasses = results[2] as List<ElectiveClass>;
        });
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
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showEditDialog({Student? student}) async {
    final nameCtrl = TextEditingController(text: student?.name ?? '');
    final studentNoCtrl = TextEditingController(text: student?.studentNo ?? '');
    String selectedGender = student?.gender ?? '男';

    final adminClasses = _classes
        .where((c) => !_isElectiveClassType(c.type))
        .toList()
      ..sort(_compareSchoolClass);
    final availableAdminClasses = adminClasses.isNotEmpty
        ? adminClasses
        : (List<SchoolClass>.from(_classes)..sort(_compareSchoolClass));

    int? selectedClassId = student?.classId != null &&
            availableAdminClasses.any((c) => c.id == student!.classId)
        ? student!.classId
        : (availableAdminClasses.isNotEmpty
            ? availableAdminClasses.first.id
            : null);

    // 选修班年级初始值
    int? electiveGradeId = _electiveClasses
            .where((c) => c.storedName == student?.electiveClass)
            .map((c) => c.gradeId)
            .firstOrNull ??
        (_grades.isNotEmpty ? _grades.first.id : null);
    String? selectedElective = student?.electiveClass;
    List<ElectiveClass> filteredElective =
        _electiveClasses.where((c) => c.gradeId == electiveGradeId).toList();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text(student == null ? '新增学生' : '编辑 ${student.name}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(
                      labelText: '姓名 *',
                      border: OutlineInputBorder(),
                      isDense: true),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: studentNoCtrl,
                  decoration: const InputDecoration(
                      labelText: '学号 *',
                      border: OutlineInputBorder(),
                      isDense: true),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    const Text('性别：'),
                    ...['男', '女'].map((g) => Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Radio<String>(
                              value: g,
                              groupValue: selectedGender,
                              onChanged: (v) =>
                                  setDialogState(() => selectedGender = v!),
                            ),
                            Text(g),
                          ],
                        )),
                  ],
                ),
                const SizedBox(height: 12),
                const Text('行政班',
                    style:
                        TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 6),
                DropdownButtonFormField<int?>(
                  value:
                      availableAdminClasses.any((c) => c.id == selectedClassId)
                          ? selectedClassId
                          : null,
                  decoration: const InputDecoration(
                      labelText: '班级 *',
                      border: OutlineInputBorder(),
                      isDense: true),
                  items: availableAdminClasses
                      .map((c) => DropdownMenuItem(
                          value: c.id, child: Text(c.displayName)))
                      .toList(),
                  onChanged: availableAdminClasses.isEmpty
                      ? null
                      : (v) => setDialogState(() => selectedClassId = v),
                ),
                if (availableAdminClasses.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 6),
                    child: Text(
                      '暂无可选行政班，请先在班级管理中创建行政班。',
                      style: TextStyle(fontSize: 12, color: Colors.redAccent),
                    ),
                  ),
                const SizedBox(height: 12),
                const Text('选修班',
                    style:
                        TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 6),
                DropdownButtonFormField<int?>(
                  value: electiveGradeId,
                  decoration: const InputDecoration(
                      labelText: '年级',
                      border: OutlineInputBorder(),
                      isDense: true),
                  items: _grades
                      .map((g) =>
                          DropdownMenuItem(value: g.id, child: Text(g.name)))
                      .toList(),
                  onChanged: (v) => setDialogState(() {
                    electiveGradeId = v;
                    filteredElective =
                        _electiveClasses.where((c) => c.gradeId == v).toList();
                    selectedElective = null;
                  }),
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String?>(
                  value: filteredElective
                          .any((c) => c.storedName == selectedElective)
                      ? selectedElective
                      : null,
                  decoration: const InputDecoration(
                      labelText: '选修班（可不选）',
                      border: OutlineInputBorder(),
                      isDense: true),
                  items: [
                    const DropdownMenuItem<String?>(
                        value: null, child: Text('— 不参加选修 —')),
                    ...filteredElective.map((c) => DropdownMenuItem(
                        value: c.storedName, child: Text(c.name))),
                  ],
                  onChanged: (v) => setDialogState(() => selectedElective = v),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('取消')),
            ElevatedButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('保存')),
          ],
        ),
      ),
    );

    if (confirmed == true) {
      if (nameCtrl.text.trim().isEmpty ||
          studentNoCtrl.text.trim().isEmpty ||
          selectedClassId == null) {
        if (mounted)
          ScaffoldMessenger.of(context)
              .showSnackBar(const SnackBar(content: Text('请填写姓名、学号并选择班级')));
        return;
      }
      try {
        await AdminService.saveStudent(
          id: student?.id,
          name: nameCtrl.text.trim(),
          gender: selectedGender,
          studentNo: studentNoCtrl.text.trim(),
          electiveClass: selectedElective,
          classId: selectedClassId!,
        );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(student == null ? '新增成功' : '修改成功')));
          _load(page: _page);
        }
      } catch (e) {
        if (mounted)
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('保存失败: $e')));
      }
    }
  }

  Future<void> _delete(Student s) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认删除'),
        content: Text('删除学生「${s.name}」及其所有考勤记录？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (ok == true) {
      try {
        await AdminService.deleteStudent(s.id);
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(const SnackBar(content: Text('删除成功')));
          _load(page: _page);
        }
      } catch (e) {
        if (mounted)
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('删除失败: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('学生管理')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showEditDialog(),
        tooltip: '新增学生',
        child: const Icon(Icons.add),
      ),
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
                    const DropdownMenuItem(value: null, child: Text('全部')),
                    ..._classes.map((c) => DropdownMenuItem(
                        value: c.id, child: Text(c.displayName))),
                  ],
                  onChanged: (v) {
                    setState(() => _selectedClassId = v);
                    _load();
                  },
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                    onPressed: () => _load(), child: const Text('搜索')),
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
                                      color:
                                          s.isMale ? Colors.blue : Colors.pink,
                                      fontWeight: FontWeight.bold)),
                            ),
                            title: Text(s.name),
                            subtitle: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                    '${s.studentNo ?? '-'} · ${s.displayClass}'),
                                if (s.electiveClass != null &&
                                    s.electiveClass!.isNotEmpty)
                                  Text('选修：${s.electiveClass}',
                                      style: const TextStyle(
                                          fontSize: 12, color: Colors.orange)),
                              ],
                            ),
                            isThreeLine: s.electiveClass != null &&
                                s.electiveClass!.isNotEmpty,
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                IconButton(
                                  icon: const Icon(Icons.edit,
                                      color: Colors.blue),
                                  onPressed: () => _showEditDialog(student: s),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.delete,
                                      color: Colors.red),
                                  onPressed: () => _delete(s),
                                ),
                              ],
                            ),
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
                    onPressed: _page < _totalPages - 1
                        ? () => _load(page: _page + 1)
                        : null,
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
