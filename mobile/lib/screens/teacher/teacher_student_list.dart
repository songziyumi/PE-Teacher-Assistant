import 'package:flutter/material.dart';
import '../../models/student.dart';
import '../../models/school_class.dart';
import '../../models/elective_class.dart';
import '../../services/teacher_service.dart';

class TeacherStudentListScreen extends StatefulWidget {
  final int classId;
  final String className;
  const TeacherStudentListScreen(
      {super.key, required this.classId, required this.className});

  @override
  State<TeacherStudentListScreen> createState() =>
      _TeacherStudentListScreenState();
}

class _TeacherStudentListScreenState extends State<TeacherStudentListScreen> {
  static const List<String> _studentStatuses = ['在籍', '休学', '毕业', '在外借读', '借读'];
  List<Student> _students = [];
  bool _loading = true;
  List<Grade> _grades = [];
  List<SchoolClass> _adminClasses = [];
  List<ElectiveClass> _electiveClasses = [];
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _loadMeta();
    _load();
  }

  Future<void> _loadMeta() async {
    try {
      final results = await Future.wait([
        TeacherService.getGrades(),
        TeacherService.getSchoolClasses(),
        TeacherService.getElectiveClasses(),
      ]);
      if (mounted)
        setState(() {
          _grades = results[0] as List<Grade>;
          _adminClasses = results[1] as List<SchoolClass>;
          _electiveClasses = results[2] as List<ElectiveClass>;
        });
    } catch (_) {}
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      _students = await TeacherService.getStudents(widget.classId);
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _reloadKeepPosition() async {
    final offset =
        _scrollController.hasClients ? _scrollController.offset : 0.0;
    try {
      final students = await TeacherService.getStudents(widget.classId);
      if (!mounted) return;
      setState(() => _students = students);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_scrollController.hasClients) return;
        final max = _scrollController.position.maxScrollExtent;
        final target = offset.clamp(0.0, max).toDouble();
        _scrollController.jumpTo(target);
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Load failed: $e')));
      }
    }
  }

  void _showEditDialog(Student s) async {
    final nameCtrl = TextEditingController(text: s.name);
    final studentNoCtrl = TextEditingController(text: s.studentNo ?? '');
    String selectedGender = (s.gender == '女') ? '女' : '男';
    String selectedStatus = _studentStatuses.contains(s.studentStatus)
        ? s.studentStatus!
        : _studentStatuses.first;

    // 行政班年级初始值
    int? adminGradeId = _adminClasses
        .where((c) => c.id == s.classId)
        .map((c) => c.gradeId)
        .firstOrNull;
    adminGradeId ??= _grades.isNotEmpty ? _grades.first.id : null;

    int? selectedClassId = s.classId;
    List<SchoolClass> filteredAdminClasses =
        _adminClasses.where((c) => c.gradeId == adminGradeId).toList();

    // 选修班年级初始值
    int? electiveGradeId = _electiveClasses
        .where((c) => c.storedName == s.electiveClass)
        .map((c) => c.gradeId)
        .firstOrNull;
    // Default elective grade follows the student's current grade.
    electiveGradeId ??= adminGradeId;
    electiveGradeId ??= _grades.isNotEmpty ? _grades.first.id : null;

    String? selectedElective = s.electiveClass;
    List<ElectiveClass> filteredElective =
        _electiveClasses.where((c) => c.gradeId == electiveGradeId).toList();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text('编辑 ${s.name}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('学号：${s.studentNo ?? '-'}',
                    style: const TextStyle(color: Colors.grey)),
                const SizedBox(height: 16),

                // ── 行政班 ──
                const Text('行政班',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 6),
                TextFormField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(
                      labelText: '学生姓名',
                      border: OutlineInputBorder(),
                      isDense: true),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: selectedGender,
                        decoration: const InputDecoration(
                            labelText: '性别',
                            border: OutlineInputBorder(),
                            isDense: true),
                        items: const [
                          DropdownMenuItem(value: '男', child: Text('男')),
                          DropdownMenuItem(value: '女', child: Text('女')),
                        ],
                        onChanged: (v) =>
                            setDialogState(() => selectedGender = v ?? '男'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: selectedStatus,
                        decoration: const InputDecoration(
                            labelText: '学籍状态',
                            border: OutlineInputBorder(),
                            isDense: true),
                        items: _studentStatuses
                            .map((status) => DropdownMenuItem(
                                value: status, child: Text(status)))
                            .toList(),
                        onChanged: (v) => setDialogState(
                            () => selectedStatus = v ?? _studentStatuses.first),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: studentNoCtrl,
                  decoration: const InputDecoration(
                      labelText: '学号',
                      border: OutlineInputBorder(),
                      isDense: true),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<int?>(
                        value: adminGradeId,
                        decoration: const InputDecoration(
                            labelText: '年级',
                            border: OutlineInputBorder(),
                            isDense: true),
                        items: _grades
                            .map((g) => DropdownMenuItem(
                                value: g.id, child: Text(g.name)))
                            .toList(),
                        onChanged: (v) => setDialogState(() {
                          adminGradeId = v;
                          filteredAdminClasses = _adminClasses
                              .where((c) => c.gradeId == v)
                              .toList();
                          selectedClassId = filteredAdminClasses.isNotEmpty
                              ? filteredAdminClasses.first.id
                              : null;
                          // Keep elective grade linked with the student's grade.
                          electiveGradeId = v;
                          filteredElective = _electiveClasses
                              .where((c) => c.gradeId == electiveGradeId)
                              .toList();
                          if (!filteredElective
                              .any((c) => c.storedName == selectedElective)) {
                            selectedElective = null;
                          }
                        }),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: DropdownButtonFormField<int?>(
                        value: filteredAdminClasses
                                .any((c) => c.id == selectedClassId)
                            ? selectedClassId
                            : null,
                        decoration: const InputDecoration(
                            labelText: '班级',
                            border: OutlineInputBorder(),
                            isDense: true),
                        items: filteredAdminClasses
                            .map((c) => DropdownMenuItem(
                                value: c.id, child: Text(c.name)))
                            .toList(),
                        onChanged: (v) =>
                            setDialogState(() => selectedClassId = v),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // ── 选修班 ──
                const Text('选修班',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<int?>(
                        value: electiveGradeId,
                        decoration: const InputDecoration(
                            labelText: '年级',
                            border: OutlineInputBorder(),
                            isDense: true),
                        items: _grades
                            .map((g) => DropdownMenuItem(
                                value: g.id, child: Text(g.name)))
                            .toList(),
                        onChanged: (v) => setDialogState(() {
                          electiveGradeId = v;
                          filteredElective = _electiveClasses
                              .where((c) => c.gradeId == v)
                              .toList();
                          selectedElective = null;
                        }),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: DropdownButtonFormField<String?>(
                        value: filteredElective
                                .any((c) => c.storedName == selectedElective)
                            ? selectedElective
                            : null,
                        decoration: const InputDecoration(
                            labelText: '班级',
                            border: OutlineInputBorder(),
                            isDense: true),
                        items: [
                          const DropdownMenuItem<String?>(
                              value: null, child: Text('— 不参加选修 —')),
                          ...filteredElective.map((c) => DropdownMenuItem(
                              value: c.storedName, child: Text(c.name))),
                        ],
                        onChanged: (v) =>
                            setDialogState(() => selectedElective = v),
                      ),
                    ),
                  ],
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
      final newName = nameCtrl.text.trim();
      final newStudentNo = studentNoCtrl.text.trim();
      if (newName.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(const SnackBar(content: Text('学生姓名不能为空')));
        }
      } else {
        final duplicated = newStudentNo.isNotEmpty &&
            _students.any((item) =>
                item.id != s.id &&
                (item.studentNo ?? '').trim() == newStudentNo);
        if (duplicated) {
          if (mounted) {
            ScaffoldMessenger.of(context)
                .showSnackBar(const SnackBar(content: Text('学号已存在，请检查后重试')));
          }
          nameCtrl.dispose();
          studentNoCtrl.dispose();
          return;
        }
        try {
          await TeacherService.updateStudent(
            s.id,
            name: newName,
            gender: selectedGender,
            studentNo: newStudentNo,
            studentStatus: selectedStatus,
            classId: selectedClassId,
            electiveClass: selectedElective,
          );
          if (mounted) {
            ScaffoldMessenger.of(context)
                .showSnackBar(const SnackBar(content: Text('修改成功')));
            _reloadKeepPosition();
          }
        } catch (e) {
          if (mounted)
            ScaffoldMessenger.of(context)
                .showSnackBar(SnackBar(content: Text('修改失败: $e')));
        }
      }
    }
    nameCtrl.dispose();
    studentNoCtrl.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.className),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(20),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text('共 ${_students.length} 名学生',
                style: const TextStyle(color: Colors.white70, fontSize: 13)),
          ),
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _students.isEmpty
              ? const Center(child: Text('暂无学生'))
              : RefreshIndicator(
                  onRefresh: _reloadKeepPosition,
                  child: ListView.builder(
                    controller: _scrollController,
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
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(s.studentNo ?? '-'),
                            if (s.studentStatus != null &&
                                s.studentStatus!.isNotEmpty)
                              Text('学籍状态：${s.studentStatus}',
                                  style: const TextStyle(
                                      fontSize: 12, color: Colors.deepPurple)),
                            if (s.displayClass.isNotEmpty)
                              Text('行政班：${s.displayClass}',
                                  style: const TextStyle(
                                      fontSize: 12, color: Colors.blueGrey)),
                            if (s.electiveClass != null &&
                                s.electiveClass!.isNotEmpty)
                              Text('选修班：${s.electiveClass}',
                                  style: const TextStyle(
                                      fontSize: 12, color: Colors.orange)),
                          ],
                        ),
                        isThreeLine: true,
                        trailing: IconButton(
                          icon: const Icon(Icons.edit, color: Colors.blue),
                          onPressed: () => _showEditDialog(s),
                        ),
                      );
                    },
                  ),
                ),
    );
  }
}
