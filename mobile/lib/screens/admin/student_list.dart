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

  // 批量操作
  bool _batchMode = false;
  final Set<int> _selectedIds = {};
  bool _batchSubmitting = false;

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
      if (mounted) {
        setState(() {
          _classes = results[0] as List<SchoolClass>;
          _grades = results[1] as List<Grade>;
          _electiveClasses = results[2] as List<ElectiveClass>;
        });
      }
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
      final visibleIds = content.map((s) => s.id).toSet();
      setState(() {
        _students = content;
        _page = data['page'] as int;
        _totalPages = data['totalPages'] as int;
        _selectedIds.removeWhere((id) => !visibleIds.contains(id));
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加载失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  static const _studentStatuses = ['在籍', '休学', '毕业', '在外借读', '借读'];

  void _showEditDialog({Student? student}) async {
    final nameCtrl = TextEditingController(text: student?.name ?? '');
    final studentNoCtrl = TextEditingController(text: student?.studentNo ?? '');
    String selectedGender = student?.gender ?? '男';
    String selectedStatus = _studentStatuses.contains(student?.studentStatus)
        ? student!.studentStatus!
        : '在籍';

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
                DropdownButtonFormField<String>(
                  initialValue: selectedGender,
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
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: selectedStatus,
                  decoration: const InputDecoration(
                      labelText: '学籍状态',
                      border: OutlineInputBorder(),
                      isDense: true),
                  items: _studentStatuses
                      .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                      .toList(),
                  onChanged: (v) =>
                      setDialogState(() => selectedStatus = v ?? '在籍'),
                ),
                const SizedBox(height: 12),
                const Text('行政班',
                    style:
                        TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 6),
                DropdownButtonFormField<int?>(
                  initialValue:
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
                  initialValue: electiveGradeId,
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
                  initialValue: filteredElective
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
      final name = nameCtrl.text.trim();
      final studentNo = studentNoCtrl.text.trim();
      if (name.isEmpty || studentNo.isEmpty || selectedClassId == null) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(const SnackBar(content: Text('请填写姓名、学号并选择班级')));
        }
        return;
      }
      try {
        final available = await AdminService.checkStudentNo(
          studentNo,
          excludeId: student?.id,
        );
        if (!available) {
          if (mounted) {
            ScaffoldMessenger.of(context)
                .showSnackBar(const SnackBar(content: Text('学号已存在，请更换学号')));
          }
          return;
        }
        await AdminService.saveStudent(
          id: student?.id,
          name: name,
          gender: selectedGender,
          studentNo: studentNo,
          electiveClass: selectedElective,
          classId: selectedClassId!,
          studentStatus: selectedStatus,
        );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(student == null ? '新增成功' : '修改成功')));
          _load(page: _page);
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('保存失败: $e')));
        }
      }
    }
  }

  // ===== 批量操作 =====

  void _toggleBatchMode() {
    setState(() {
      _batchMode = !_batchMode;
      _selectedIds.clear();
    });
  }

  bool get _allVisibleSelected =>
      _students.isNotEmpty && _students.every((s) => _selectedIds.contains(s.id));

  void _toggleSelectAll() {
    setState(() {
      if (_allVisibleSelected) {
        _selectedIds.removeAll(_students.map((s) => s.id));
      } else {
        _selectedIds.addAll(_students.map((s) => s.id));
      }
    });
  }

  Future<bool> _confirmBatchOperation({
    required String title,
    required String message,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          ElevatedButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('确认')),
        ],
      ),
    );
    return confirmed == true;
  }

  Future<void> _executeBatchOperation({
    required String actionLabel,
    required List<int> studentIds,
    required Future<Map<String, dynamic>> Function(List<int>) operation,
  }) async {
    if (_batchSubmitting || studentIds.isEmpty) return;
    setState(() => _batchSubmitting = true);
    try {
      final result = await operation(studentIds);
      if (!mounted) return;
      await _load(page: _page);
      if (!mounted) return;
      final failedIds = ((result['failedItems'] as List?) ?? [])
          .map((item) => (item as Map)['id'])
          .whereType<num>()
          .map((n) => n.toInt())
          .toSet();
      setState(() {
        _selectedIds
          ..clear()
          ..addAll(_students.where((s) => failedIds.contains(s.id)).map((s) => s.id));
      });
      await _showBatchResultDialog(actionLabel, result,
          onRetry: failedIds.isEmpty
              ? null
              : (retryIds) => _executeBatchOperation(
                    actionLabel: actionLabel,
                    studentIds: retryIds,
                    operation: operation,
                  ));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$actionLabel失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _batchSubmitting = false);
    }
  }

  Future<void> _showBatchResultDialog(
    String actionLabel,
    Map<String, dynamic> result, {
    Future<void> Function(List<int>)? onRetry,
  }) {
    final totalCount = result['totalCount'] ?? 0;
    final successCount = result['successCount'] ?? 0;
    final failedCount = result['failedCount'] ?? 0;
    final failedItems = ((result['failedItems'] as List?) ?? [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final failedIds = failedItems
        .map((e) => e['id'])
        .whereType<num>()
        .map((n) => n.toInt())
        .toList();
    return showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(actionLabel),
        content: SizedBox(
          width: 340,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('总计：$totalCount'),
              Text('成功：$successCount'),
              Text('失败：$failedCount'),
              if (failedItems.isNotEmpty) ...[
                const SizedBox(height: 12),
                const Text('失败明细', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 6),
                SizedBox(
                  height: 140,
                  child: ListView.separated(
                    shrinkWrap: true,
                    itemCount: failedItems.length,
                    separatorBuilder: (_, __) => const Divider(height: 10),
                    itemBuilder: (_, i) {
                      final item = failedItems[i];
                      return Text(
                        'ID ${item['id'] ?? '-'}：${item['reason'] ?? ''}',
                        style: const TextStyle(fontSize: 13),
                      );
                    },
                  ),
                ),
              ],
            ],
          ),
        ),
        actions: [
          if (onRetry != null && failedIds.isNotEmpty)
            TextButton(
              onPressed: _batchSubmitting
                  ? null
                  : () async {
                      Navigator.pop(ctx);
                      await onRetry(failedIds);
                    },
              child: const Text('重试失败项'),
            ),
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('我知道了')),
        ],
      ),
    );
  }

  Future<void> _showBatchStatusDialog() async {
    if (_selectedIds.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请先选择学生')));
      return;
    }
    String selectedStatus = '在籍';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDs) => AlertDialog(
          title: const Text('批量修改学籍状态'),
          content: DropdownButtonFormField<String>(
            initialValue: selectedStatus,
            isExpanded: true,
            decoration: const InputDecoration(
                labelText: '学籍状态', border: OutlineInputBorder(), isDense: true),
            items: _studentStatuses
                .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                .toList(),
            onChanged: (v) => setDs(() => selectedStatus = v ?? '在籍'),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('取消')),
            ElevatedButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('下一步')),
          ],
        ),
      ),
    );
    if (confirmed != true) return;
    final ok = await _confirmBatchOperation(
      title: '确认批量修改学籍',
      message: '确认将 ${_selectedIds.length} 名学生学籍状态修改为「$selectedStatus」吗？',
    );
    if (!ok) return;
    await _executeBatchOperation(
      actionLabel: '批量修改学籍状态',
      studentIds: _selectedIds.toList(),
      operation: (ids) => AdminService.batchUpdateStudentStatus(ids, selectedStatus),
    );
  }

  Future<void> _showBatchElectiveClassDialog() async {
    if (_selectedIds.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请先选择学生')));
      return;
    }
    if (_electiveClasses.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('暂无可分配的选修班')));
      return;
    }
    int? electiveGradeId = _grades.isNotEmpty ? _grades.first.id : null;
    List<ElectiveClass> filtered =
        _electiveClasses.where((c) => c.gradeId == electiveGradeId).toList();
    String? selectedElective = filtered.isNotEmpty ? filtered.first.storedName : null;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDs) => AlertDialog(
          title: const Text('批量分配选修班'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<int?>(
                initialValue: electiveGradeId,
                isExpanded: true,
                decoration: const InputDecoration(
                    labelText: '年级', border: OutlineInputBorder(), isDense: true),
                items: _grades
                    .map((g) => DropdownMenuItem(value: g.id, child: Text(g.name)))
                    .toList(),
                onChanged: (v) => setDs(() {
                  electiveGradeId = v;
                  filtered = _electiveClasses.where((c) => c.gradeId == v).toList();
                  selectedElective = filtered.isNotEmpty ? filtered.first.storedName : null;
                }),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String?>(
                initialValue: selectedElective,
                isExpanded: true,
                decoration: const InputDecoration(
                    labelText: '选修班', border: OutlineInputBorder(), isDense: true),
                items: filtered
                    .map((c) => DropdownMenuItem(value: c.storedName, child: Text(c.name)))
                    .toList(),
                onChanged: (v) => setDs(() => selectedElective = v),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('取消')),
            ElevatedButton(
              onPressed: selectedElective == null ? null : () => Navigator.pop(ctx, true),
              child: const Text('下一步'),
            ),
          ],
        ),
      ),
    );
    if (confirmed != true || selectedElective == null) return;
    final displayName = _electiveClasses
        .firstWhere((c) => c.storedName == selectedElective,
            orElse: () => _electiveClasses.first)
        .displayName;
    final ok = await _confirmBatchOperation(
      title: '确认批量分配选修班',
      message: '确认将 ${_selectedIds.length} 名学生分配到「$displayName」吗？',
    );
    if (!ok) return;
    await _executeBatchOperation(
      actionLabel: '批量分配选修班',
      studentIds: _selectedIds.toList(),
      operation: (ids) => AdminService.batchUpdateStudentElectiveClass(ids,
          electiveClass: selectedElective),
    );
  }

  Future<void> _confirmBatchClearElective() async {
    if (_selectedIds.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请先选择学生')));
      return;
    }
    final ok = await _confirmBatchOperation(
      title: '批量清空选修班',
      message: '确认清空 ${_selectedIds.length} 名学生的选修班吗？',
    );
    if (!ok) return;
    await _executeBatchOperation(
      actionLabel: '批量清空选修班',
      studentIds: _selectedIds.toList(),
      operation: (ids) =>
          AdminService.batchUpdateStudentElectiveClass(ids, electiveClass: null),
    );
  }

  Future<void> _confirmBatchDelete() async {
    if (_selectedIds.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请先选择学生')));
      return;
    }
    // 第一次确认
    final first = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('批量删除学生'),
        content: Text('即将删除 ${_selectedIds.length} 名学生及其所有考勤记录，确认继续？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('继续'),
          ),
        ],
      ),
    );
    if (first != true) return;
    // 二次确认
    final second = await _confirmBatchOperation(
      title: '再次确认删除',
      message: '此操作不可恢复！确认删除 ${_selectedIds.length} 名学生吗？',
    );
    if (!second) return;
    await _executeBatchOperation(
      actionLabel: '批量删除',
      studentIds: _selectedIds.toList(),
      operation: AdminService.batchDeleteStudents,
    );
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
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('删除失败: $e')));
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final selectedCount = _selectedIds.length;
    return Scaffold(
      appBar: AppBar(
        title: _batchMode
            ? Text('已选 $selectedCount 名学生')
            : const Text('学生管理'),
        actions: [
          if (_batchMode) ...[
            TextButton(
              onPressed: _toggleSelectAll,
              child: Text(_allVisibleSelected ? '取消全选' : '全选',
                  style: const TextStyle(color: Colors.white)),
            ),
            TextButton(
              onPressed: _toggleBatchMode,
              child: const Text('退出', style: TextStyle(color: Colors.white)),
            ),
          ] else
            IconButton(
              icon: const Icon(Icons.checklist),
              tooltip: '批量操作',
              onPressed: _toggleBatchMode,
            ),
        ],
      ),
      floatingActionButton: _batchMode
          ? null
          : FloatingActionButton(
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
                          final selected = _selectedIds.contains(s.id);
                          return ListTile(
                            leading: _batchMode
                                ? Checkbox(
                                    value: selected,
                                    onChanged: (_) => setState(() {
                                      if (selected) {
                                        _selectedIds.remove(s.id);
                                      } else {
                                        _selectedIds.add(s.id);
                                      }
                                    }),
                                  )
                                : CircleAvatar(
                                    backgroundColor: s.isMale
                                        ? Colors.blue.shade100
                                        : Colors.pink.shade100,
                                    child: Text(s.gender ?? '?',
                                        style: TextStyle(
                                            color: s.isMale
                                                ? Colors.blue
                                                : Colors.pink,
                                            fontWeight: FontWeight.bold)),
                                  ),
                            title: Text(s.name),
                            subtitle: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('${s.studentNo ?? '-'} · ${s.displayClass}'),
                                if (s.electiveClass != null &&
                                    s.electiveClass!.isNotEmpty)
                                  Text('选修：${s.electiveClass}',
                                      style: const TextStyle(
                                          fontSize: 12, color: Colors.orange)),
                                if (s.studentStatus != null &&
                                    s.studentStatus != '在籍')
                                  Text('学籍：${s.studentStatus}',
                                      style: const TextStyle(
                                          fontSize: 12, color: Colors.grey)),
                              ],
                            ),
                            isThreeLine: (s.electiveClass != null &&
                                    s.electiveClass!.isNotEmpty) ||
                                (s.studentStatus != null &&
                                    s.studentStatus != '在籍'),
                            selected: _batchMode && selected,
                            onTap: _batchMode
                                ? () => setState(() {
                                      if (selected) {
                                        _selectedIds.remove(s.id);
                                      } else {
                                        _selectedIds.add(s.id);
                                      }
                                    })
                                : null,
                            trailing: _batchMode
                                ? null
                                : Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      IconButton(
                                        icon: const Icon(Icons.edit,
                                            color: Colors.blue),
                                        onPressed: () =>
                                            _showEditDialog(student: s),
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
          // 批量操作栏
          if (_batchMode)
            _BatchActionBar(
              selectedCount: selectedCount,
              submitting: _batchSubmitting,
              onStatus: _showBatchStatusDialog,
              onAssignElective: _showBatchElectiveClassDialog,
              onClearElective: _confirmBatchClearElective,
              onDelete: _confirmBatchDelete,
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

class _BatchActionBar extends StatelessWidget {
  final int selectedCount;
  final bool submitting;
  final VoidCallback onStatus;
  final VoidCallback onAssignElective;
  final VoidCallback onClearElective;
  final VoidCallback onDelete;

  const _BatchActionBar({
    required this.selectedCount,
    required this.submitting,
    required this.onStatus,
    required this.onAssignElective,
    required this.onClearElective,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final enabled = selectedCount > 0 && !submitting;
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      child: submitting
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(8),
                child: CircularProgressIndicator(),
              ),
            )
          : Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _ActionBtn(
                  icon: Icons.badge,
                  label: '学籍状态',
                  onTap: enabled ? onStatus : null,
                ),
                _ActionBtn(
                  icon: Icons.class_,
                  label: '分配选修',
                  onTap: enabled ? onAssignElective : null,
                ),
                _ActionBtn(
                  icon: Icons.remove_circle_outline,
                  label: '清空选修',
                  onTap: enabled ? onClearElective : null,
                ),
                _ActionBtn(
                  icon: Icons.delete_sweep,
                  label: '删除',
                  color: Colors.red,
                  onTap: enabled ? onDelete : null,
                ),
              ],
            ),
    );
  }
}

class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final Color? color;

  const _ActionBtn({
    required this.icon,
    required this.label,
    this.onTap,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? Theme.of(context).colorScheme.primary;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: onTap == null ? Colors.grey : c, size: 22),
            const SizedBox(height: 2),
            Text(label,
                style: TextStyle(
                    fontSize: 11,
                    color: onTap == null ? Colors.grey : c)),
          ],
        ),
      ),
    );
  }
}
