import 'dart:async';
import 'package:flutter/material.dart';
import '../../models/student.dart';
import '../../models/school_class.dart';
import '../../models/elective_class.dart';
import '../../services/teacher_service.dart';

class TeacherStudentListScreen extends StatefulWidget {
  final int classId;
  final String className;

  const TeacherStudentListScreen({
    super.key,
    required this.classId,
    required this.className,
  });

  @override
  State<TeacherStudentListScreen> createState() =>
      _TeacherStudentListScreenState();
}

class _TeacherStudentListScreenState extends State<TeacherStudentListScreen> {
  static const List<String> _studentStatuses = ['在籍', '休学', '毕业', '在外借读', '借读'];
  static const Color _classGroupColorA = Color(0xFFDDEEFF);
  static const Color _classGroupColorB = Color(0xFFFFE2C2);
  static const int _studentNameMaxLength = 50;
  static const int _studentNoMaxLength = 50;

  List<Student> _students = [];
  bool _loading = true;
  List<Grade> _grades = [];
  List<SchoolClass> _adminClasses = [];
  List<ElectiveClass> _electiveClasses = [];
  final ScrollController _scrollController = ScrollController();
  final TextEditingController _nameFilterCtrl = TextEditingController();
  final TextEditingController _studentNoFilterCtrl = TextEditingController();
  int? _selectedAdminClassId;
  String? _selectedElectiveClass;
  String? _selectedStudentStatus;
  bool _filterExpanded = false;

  @override
  void initState() {
    super.initState();
    _loadMeta();
    _load();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    _nameFilterCtrl.dispose();
    _studentNoFilterCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadMeta() async {
    try {
      final results = await Future.wait([
        TeacherService.getGrades(),
        TeacherService.getSchoolClasses(),
        TeacherService.getElectiveClasses(),
      ]);
      if (!mounted) return;
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
      final students = await _fetchStudents();
      if (!mounted) return;
      setState(() => _students = students);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _reloadKeepPosition() async {
    final offset =
        _scrollController.hasClients ? _scrollController.offset : 0.0;
    try {
      final students = await _fetchStudents();
      if (!mounted) return;
      setState(() => _students = students);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_scrollController.hasClients) return;
        final max = _scrollController.position.maxScrollExtent;
        final target = offset.clamp(0.0, max).toDouble();
        _scrollController.jumpTo(target);
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    }
  }

  Future<List<Student>> _fetchStudents() {
    return TeacherService.getStudents(
      widget.classId,
      name: _nameFilterCtrl.text,
      studentNo: _studentNoFilterCtrl.text,
      adminClassId: _selectedAdminClassId,
      electiveClass: _selectedElectiveClass,
      studentStatus: _selectedStudentStatus,
    );
  }

  void _applyFilters() {
    _load();
  }

  void _resetFilters() {
    setState(() {
      _nameFilterCtrl.clear();
      _studentNoFilterCtrl.clear();
      _selectedAdminClassId = null;
      _selectedElectiveClass = null;
      _selectedStudentStatus = null;
    });
    _load();
  }

  int get _activeFilterCount {
    var count = 0;
    if (_nameFilterCtrl.text.trim().isNotEmpty) count++;
    if (_studentNoFilterCtrl.text.trim().isNotEmpty) count++;
    if (_selectedAdminClassId != null) count++;
    if (_selectedElectiveClass != null && _selectedElectiveClass!.isNotEmpty) {
      count++;
    }
    if (_selectedStudentStatus != null && _selectedStudentStatus!.isNotEmpty) {
      count++;
    }
    return count;
  }

  int _compareSchoolClass(SchoolClass a, SchoolClass b) {
    final gradeCompare = (a.gradeName ?? '').compareTo(b.gradeName ?? '');
    if (gradeCompare != 0) return gradeCompare;
    return a.name.compareTo(b.name);
  }

  List<SchoolClass> get _sortedAdminClasses {
    final list = List<SchoolClass>.from(_adminClasses);
    list.sort(_compareSchoolClass);
    return list;
  }

  List<ElectiveClass> get _sortedElectiveClasses {
    final list = List<ElectiveClass>.from(_electiveClasses);
    list.sort((a, b) {
      final gradeCompare = (a.gradeName ?? '').compareTo(b.gradeName ?? '');
      if (gradeCompare != 0) return gradeCompare;
      return a.name.compareTo(b.name);
    });
    return list;
  }

  int? _findAdminGradeIdByClassId(int? classId) {
    if (classId == null) return null;
    for (final c in _adminClasses) {
      if (c.id == classId) return c.gradeId;
    }
    return null;
  }

  int? _findElectiveGradeIdByStoredName(String? storedName) {
    if (storedName == null || storedName.trim().isEmpty) return null;
    for (final c in _electiveClasses) {
      if (c.storedName == storedName) return c.gradeId;
    }
    return null;
  }

  String _classGroupKey(Student s) {
    final key = s.displayClass.trim();
    return key.isEmpty ? '未分班' : key;
  }

  List<Student> _buildDisplayStudents() {
    final sorted = List<Student>.from(_students);
    sorted.sort((a, b) {
      final classCompare = _classGroupKey(a).compareTo(_classGroupKey(b));
      if (classCompare != 0) return classCompare;
      final nameCompare = a.name.compareTo(b.name);
      if (nameCompare != 0) return nameCompare;
      return (a.studentNo ?? '').compareTo(b.studentNo ?? '');
    });
    return sorted;
  }

  Map<String, Color> _buildClassGroupColors(List<Student> students) {
    final colors = <String, Color>{};
    var classIndex = 0;
    for (final s in students) {
      final key = _classGroupKey(s);
      colors.putIfAbsent(key, () {
        final color = classIndex.isEven ? _classGroupColorA : _classGroupColorB;
        classIndex++;
        return color;
      });
    }
    return colors;
  }

  String? _validateStudentName(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      return '学生姓名不能为空';
    }
    if (trimmed.length > _studentNameMaxLength) {
      return '学生姓名不能超过 $_studentNameMaxLength 个字符';
    }
    return null;
  }

  String? _validateStudentNo(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      return '学号不能为空';
    }
    if (trimmed.length > _studentNoMaxLength) {
      return '学号不能超过 $_studentNoMaxLength 个字符';
    }
    if (trimmed.contains(RegExp(r'\s'))) {
      return '学号不能包含空格';
    }
    return null;
  }

  String _buildStudentSaveErrorMessage(Object error) {
    final message = error.toString().trim();
    if (message.contains('学号已存在')) {
      return '学号已存在，请修改后重试';
    }
    if (message.contains('学籍状态不合法')) {
      return '学籍状态不合法，请重新选择';
    }
    return '修改失败：$message';
  }

  Widget _buildFilterPanel() {
    final adminClasses = _sortedAdminClasses;
    final electiveClasses = _sortedElectiveClasses;
    return Card(
      margin: const EdgeInsets.fromLTRB(12, 12, 12, 6),
      child: ExpansionTile(
        initiallyExpanded: _filterExpanded,
        onExpansionChanged: (expanded) =>
            setState(() => _filterExpanded = expanded),
        title: Text('筛选条件（已启用 $_activeFilterCount 项）'),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _nameFilterCtrl,
                        decoration: const InputDecoration(
                          labelText: '姓名',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                        onSubmitted: (_) => _applyFilters(),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _studentNoFilterCtrl,
                        decoration: const InputDecoration(
                          labelText: '学号',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                        onSubmitted: (_) => _applyFilters(),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<int?>(
                  initialValue: _selectedAdminClassId,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: '行政班',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: [
                    const DropdownMenuItem<int?>(
                      value: null,
                      child: Text('全部行政班'),
                    ),
                    ...adminClasses.map(
                      (c) => DropdownMenuItem<int?>(
                        value: c.id,
                        child: Text(c.displayName),
                      ),
                    ),
                  ],
                  onChanged: (v) => setState(() => _selectedAdminClassId = v),
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<String?>(
                  initialValue: _selectedElectiveClass,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: '选修班',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: [
                    const DropdownMenuItem<String?>(
                      value: null,
                      child: Text('全部选修班'),
                    ),
                    ...electiveClasses.map(
                      (c) => DropdownMenuItem<String?>(
                        value: c.storedName,
                        child: Text(c.displayName),
                      ),
                    ),
                  ],
                  onChanged: (v) => setState(() => _selectedElectiveClass = v),
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<String?>(
                  initialValue: _selectedStudentStatus,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: '学籍状态',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: [
                    const DropdownMenuItem<String?>(
                      value: null,
                      child: Text('全部状态'),
                    ),
                    ..._studentStatuses.map(
                      (status) => DropdownMenuItem<String?>(
                        value: status,
                        child: Text(status),
                      ),
                    ),
                  ],
                  onChanged: (v) => setState(() => _selectedStudentStatus = v),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _loading ? null : _resetFilters,
                        child: const Text('重置'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: _loading ? null : _applyFilters,
                        child: const Text('查询'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _showEditDialog(Student s) async {
    final messenger = ScaffoldMessenger.of(context);
    final nameCtrl = TextEditingController(text: s.name);
    final studentNoCtrl = TextEditingController(text: s.studentNo ?? '');
    final originalStudentNo = (s.studentNo ?? '').trim();

    String selectedGender = (s.gender == '女') ? '女' : '男';
    String selectedStatus = _studentStatuses.contains(s.studentStatus)
        ? s.studentStatus!
        : _studentStatuses.first;

    int? adminGradeId = _findAdminGradeIdByClassId(s.classId);
    adminGradeId ??= _grades.isNotEmpty ? _grades.first.id : null;
    int? selectedClassId = s.classId;
    List<SchoolClass> filteredAdminClasses =
        _adminClasses.where((c) => c.gradeId == adminGradeId).toList();

    int? electiveGradeId = _findElectiveGradeIdByStoredName(s.electiveClass);
    electiveGradeId ??= adminGradeId;
    electiveGradeId ??= _grades.isNotEmpty ? _grades.first.id : null;
    String? selectedElective = s.electiveClass;
    List<ElectiveClass> filteredElective =
        _electiveClasses.where((c) => c.gradeId == electiveGradeId).toList();

    Timer? studentNoCheckDebounce;
    bool studentNoChecking = false;
    bool? studentNoAvailable;
    bool saving = false;
    bool dialogAlive = true;
    String? nameError;
    String? studentNoError;
    String? formError;

    void safeSetDialogState(
      void Function(void Function()) setDialogState,
      void Function() update,
    ) {
      if (!dialogAlive) return;
      setDialogState(update);
    }

    Future<void> runStudentNoCheck(
      String input,
      void Function(void Function()) setDialogState,
    ) async {
      if (!dialogAlive) return;
      final value = input.trim();
      if (value.isEmpty || value == originalStudentNo) {
        safeSetDialogState(setDialogState, () {
          studentNoChecking = false;
          studentNoAvailable = null;
          studentNoError = null;
        });
        return;
      }

      final validationError = _validateStudentNo(value);
      if (validationError != null) {
        safeSetDialogState(setDialogState, () {
          studentNoChecking = false;
          studentNoAvailable = null;
          studentNoError = validationError;
        });
        return;
      }

      safeSetDialogState(setDialogState, () => studentNoChecking = true);
      try {
        final result = await TeacherService.checkStudentNoAvailability(
          value,
          excludeId: s.id,
        );
        if (!dialogAlive) return;
        if (studentNoCtrl.text.trim() != value) return;
        safeSetDialogState(setDialogState, () {
          studentNoChecking = false;
          studentNoAvailable = result['available'] == true;
          studentNoError =
              studentNoAvailable == true ? null : result['message']?.toString();
        });
      } catch (_) {
        if (!dialogAlive) return;
        if (studentNoCtrl.text.trim() != value) return;
        safeSetDialogState(setDialogState, () {
          studentNoChecking = false;
          studentNoAvailable = null;
          studentNoError = null;
        });
      }
    }

    void scheduleStudentNoCheck(
      String input,
      void Function(void Function()) setDialogState,
    ) {
      if (!dialogAlive) return;
      studentNoCheckDebounce?.cancel();
      studentNoCheckDebounce = Timer(const Duration(milliseconds: 350), () {
        if (!dialogAlive) return;
        runStudentNoCheck(input, setDialogState);
      });
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text('编辑 ${s.name}'),
          content: LayoutBuilder(
            builder: (context, constraints) {
              final isNarrow = constraints.maxWidth < 360;

              Widget buildFieldPair(Widget first, Widget second) {
                if (isNarrow) {
                  return Column(
                    children: [
                      first,
                      const SizedBox(height: 12),
                      second,
                    ],
                  );
                }
                return Row(
                  children: [
                    Expanded(child: first),
                    const SizedBox(width: 8),
                    Expanded(child: second),
                  ],
                );
              }

              return SingleChildScrollView(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 420),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('当前学号：${s.studentNo ?? '-'}',
                          style: const TextStyle(color: Colors.grey)),
                      const SizedBox(height: 16),
                      const Text('基础信息',
                          style: TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 6),
                      TextFormField(
                        controller: nameCtrl,
                        maxLength: _studentNameMaxLength,
                        decoration: InputDecoration(
                          labelText: '学生姓名',
                          border: const OutlineInputBorder(),
                          isDense: true,
                          errorText: nameError,
                        ),
                        onChanged: (_) {
                          if (nameError == null && formError == null) return;
                          setDialogState(() {
                            nameError = null;
                            formError = null;
                          });
                        },
                      ),
                      const SizedBox(height: 16),
                      buildFieldPair(
                        DropdownButtonFormField<String>(
                          initialValue: selectedGender,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: '性别',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: const [
                            DropdownMenuItem(value: '男', child: Text('男')),
                            DropdownMenuItem(value: '女', child: Text('女')),
                          ],
                          onChanged: (v) =>
                              setDialogState(() => selectedGender = v ?? '男'),
                        ),
                        DropdownButtonFormField<String>(
                          initialValue: selectedStatus,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: '学籍状态',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: _studentStatuses
                              .map((status) => DropdownMenuItem(
                                  value: status, child: Text(status)))
                              .toList(),
                          onChanged: (v) => setDialogState(
                            () => selectedStatus = v ?? _studentStatuses.first,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: studentNoCtrl,
                        maxLength: _studentNoMaxLength,
                        decoration: InputDecoration(
                          labelText: '学号',
                          border: const OutlineInputBorder(),
                          isDense: true,
                          helperText: studentNoChecking
                              ? '正在校验学号...'
                              : (studentNoAvailable == true ? '学号可用' : null),
                          errorText: studentNoError,
                          suffixIcon: studentNoChecking
                              ? const Padding(
                                  padding: EdgeInsets.all(12),
                                  child: SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2),
                                  ),
                                )
                              : (studentNoAvailable == null
                                  ? null
                                  : Icon(
                                      studentNoAvailable == true
                                          ? Icons.check_circle
                                          : Icons.cancel,
                                      color: studentNoAvailable == true
                                          ? Colors.green
                                          : Colors.red,
                                    )),
                        ),
                        onChanged: (v) {
                          setDialogState(() {
                            formError = null;
                            studentNoError = null;
                            if (v.trim().isEmpty || v.trim() == originalStudentNo) {
                              studentNoAvailable = null;
                            }
                          });
                          scheduleStudentNoCheck(v, setDialogState);
                        },
                      ),
                      if (formError != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          formError!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                            fontSize: 12,
                          ),
                        ),
                      ],
                      const SizedBox(height: 16),
                      const Text('行政班',
                          style: TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 6),
                      buildFieldPair(
                        DropdownButtonFormField<int?>(
                          initialValue: adminGradeId,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: '年级',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: _grades
                              .map((g) => DropdownMenuItem(
                                    value: g.id,
                                    child: Text(g.name),
                                  ))
                              .toList(),
                          onChanged: (v) => setDialogState(() {
                            adminGradeId = v;
                            filteredAdminClasses = _adminClasses
                                .where((c) => c.gradeId == v)
                                .toList();
                            selectedClassId = filteredAdminClasses.isNotEmpty
                                ? filteredAdminClasses.first.id
                                : null;
                            electiveGradeId = v;
                            filteredElective = _electiveClasses
                                .where((c) => c.gradeId == electiveGradeId)
                                .toList();
                            if (!filteredElective
                                .any((c) => c.storedName == selectedElective)) {
                              selectedElective = null;
                            }
                            formError = null;
                          }),
                        ),
                        DropdownButtonFormField<int?>(
                          initialValue: filteredAdminClasses
                                  .any((c) => c.id == selectedClassId)
                              ? selectedClassId
                              : null,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: '班级',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: filteredAdminClasses
                              .map((c) => DropdownMenuItem(
                                    value: c.id,
                                    child: Text(c.name),
                                  ))
                              .toList(),
                          onChanged: (v) => setDialogState(() {
                            selectedClassId = v;
                            formError = null;
                          }),
                        ),
                      ),
                      const SizedBox(height: 16),
                      const Text('选修班',
                          style: TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 6),
                      buildFieldPair(
                        DropdownButtonFormField<int?>(
                          initialValue: electiveGradeId,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: '年级',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: _grades
                              .map((g) => DropdownMenuItem(
                                    value: g.id,
                                    child: Text(g.name),
                                  ))
                              .toList(),
                          onChanged: (v) => setDialogState(() {
                            electiveGradeId = v;
                            filteredElective = _electiveClasses
                                .where((c) => c.gradeId == v)
                                .toList();
                            selectedElective = null;
                          }),
                        ),
                        DropdownButtonFormField<String?>(
                          initialValue: filteredElective
                                  .any((c) => c.storedName == selectedElective)
                              ? selectedElective
                              : null,
                          isExpanded: true,
                          decoration: const InputDecoration(
                            labelText: '班级',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: [
                            const DropdownMenuItem<String?>(
                              value: null,
                              child: Text('（不参加选修）'),
                            ),
                            ...filteredElective.map((c) => DropdownMenuItem(
                                  value: c.storedName,
                                  child: Text(c.name),
                                )),
                          ],
                          onChanged: (v) =>
                              setDialogState(() => selectedElective = v),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
          actions: [
            TextButton(
              onPressed: () {
                dialogAlive = false;
                studentNoCheckDebounce?.cancel();
                Navigator.pop(ctx, false);
              },
              child: const Text('取消'),
            ),
            ElevatedButton(
              onPressed: saving
                  ? null
                  : () async {
                      final newName = nameCtrl.text.trim();
                      final newStudentNo = studentNoCtrl.text.trim();
                      final validatedNameError = _validateStudentName(newName);
                      final validatedStudentNoError =
                          _validateStudentNo(newStudentNo);

                      if (validatedNameError != null ||
                          validatedStudentNoError != null) {
                        safeSetDialogState(setDialogState, () {
                          nameError = validatedNameError;
                          studentNoError = validatedStudentNoError;
                          formError = null;
                        });
                        return;
                      }

                      if (selectedClassId == null) {
                        safeSetDialogState(setDialogState, () {
                          formError = '行政班不能为空，请先选择行政班';
                        });
                        return;
                      }

                      final duplicated = _students.any((item) =>
                          item.id != s.id &&
                          (item.studentNo ?? '').trim() == newStudentNo);
                      if (duplicated) {
                        safeSetDialogState(setDialogState, () {
                          studentNoAvailable = false;
                          studentNoError = '学号已存在';
                          formError = null;
                        });
                        return;
                      }

                      if (newStudentNo != originalStudentNo) {
                        safeSetDialogState(setDialogState, () {
                          studentNoChecking = true;
                          studentNoError = null;
                        });
                        try {
                          final check =
                              await TeacherService.checkStudentNoAvailability(
                            newStudentNo,
                            excludeId: s.id,
                          );
                          if (!dialogAlive) return;
                          if (check['available'] != true) {
                            safeSetDialogState(setDialogState, () {
                              studentNoChecking = false;
                              studentNoAvailable = false;
                              studentNoError =
                                  check['message']?.toString() ?? '学号已存在';
                            });
                            return;
                          }
                          safeSetDialogState(setDialogState, () {
                            studentNoChecking = false;
                            studentNoAvailable = true;
                          });
                        } catch (error) {
                          safeSetDialogState(setDialogState, () {
                            studentNoChecking = false;
                            formError = '学号校验失败：$error';
                          });
                          return;
                        }
                      }

                      safeSetDialogState(setDialogState, () {
                        saving = true;
                        nameError = null;
                        studentNoError = null;
                        formError = null;
                      });

                      try {
                        await TeacherService.updateStudent(
                          s.id,
                          name: newName,
                          gender: selectedGender,
                          studentNo: newStudentNo,
                          studentStatus: selectedStatus,
                          classId: selectedClassId,
                          electiveClass: selectedElective,
                          version: s.version,
                        );
                        if (!dialogAlive) return;
                        if (!ctx.mounted) return;
                        dialogAlive = false;
                        studentNoCheckDebounce?.cancel();
                        Navigator.pop(ctx, true);
                      } catch (error) {
                        safeSetDialogState(setDialogState, () {
                          saving = false;
                          final message =
                              _buildStudentSaveErrorMessage(error);
                          if (message.contains('学号已存在')) {
                            studentNoAvailable = false;
                            studentNoError = '学号已存在，请修改后重试';
                            formError = null;
                          } else if (message.contains('该学生已被其他设备修改')) {
                            formError = '该学生已被其他设备修改，请关闭后重新打开再保存';
                          } else if (message.contains('学号不能为空') ||
                              message.contains('学号不能超过') ||
                              message.contains('学号不能包含空格')) {
                            studentNoError = message;
                            formError = null;
                          } else if (message.contains('学生姓名不能为空') ||
                              message.contains('学生姓名不能超过')) {
                            nameError = message;
                            formError = null;
                          } else {
                            formError = message;
                          }
                        });
                      }
                    },
              child: Text(saving ? '保存中...' : '保存'),
            ),
          ],
        ),
      ),
    );

    dialogAlive = false;

    studentNoCheckDebounce?.cancel();
    nameCtrl.dispose();
    studentNoCtrl.dispose();

    if (confirmed == true && mounted) {
      messenger.showSnackBar(const SnackBar(content: Text('修改成功')));
      _reloadKeepPosition();
    }
  }

  Future<void> _showAttendanceDialog(Student s) async {
    final messenger = ScaffoldMessenger.of(context);
    await showDialog<void>(
      context: context,
      builder: (ctx) {
        return FutureBuilder<Map<String, dynamic>>(
          future: TeacherService.getStudentAttendanceHistory(s.id, days: 90),
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const AlertDialog(
                content: SizedBox(
                  height: 80,
                  child: Center(child: CircularProgressIndicator()),
                ),
              );
            }

            if (snapshot.hasError || !snapshot.hasData) {
              return AlertDialog(
                title: const Text('出勤记录'),
                content: Text('加载失败：${snapshot.error ?? '未知错误'}'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(ctx),
                    child: const Text('关闭'),
                  ),
                ],
              );
            }

            final data = snapshot.data!;
            final stats = (data['stats'] as Map?)?.cast<String, dynamic>() ??
                <String, dynamic>{};
            final records = (data['records'] as List?) ?? const [];

            Color statusColor(String value) {
              switch (value) {
                case '缺勤':
                  return Colors.red;
                case '请假':
                  return Colors.orange;
                default:
                  return Colors.green;
              }
            }

            return AlertDialog(
              title: Text('${s.name} - 出勤记录'),
              content: SizedBox(
                width: 360,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('近90天共 ${stats['total'] ?? 0} 次'),
                    Text(
                        '出勤：${stats['present'] ?? 0}  缺勤：${stats['absent'] ?? 0}  请假：${stats['leave'] ?? 0}'),
                    Text('出勤率：${stats['rate'] ?? '0.0'}%'),
                    const SizedBox(height: 10),
                    if (records.isEmpty)
                      const Text('暂无出勤记录')
                    else
                      SizedBox(
                        height: 220,
                        child: ListView.builder(
                          itemCount: records.length,
                          itemBuilder: (_, i) {
                            final item =
                                (records[i] as Map).cast<String, dynamic>();
                            final status = item['status']?.toString() ?? '-';
                            return ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              title: Text(item['date']?.toString() ?? '-'),
                              trailing: Text(
                                status,
                                style: TextStyle(
                                  color: statusColor(status),
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('关闭'),
                ),
              ],
            );
          },
        );
      },
    );
    messenger.hideCurrentSnackBar();
  }

  @override
  Widget build(BuildContext context) {
    final displayStudents = _buildDisplayStudents();
    final classGroupColors = _buildClassGroupColors(displayStudents);

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.className),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(20),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              '共 ${displayStudents.length} 名学生',
              style: const TextStyle(color: Colors.white70, fontSize: 13),
            ),
          ),
        ),
      ),
      body: Column(
        children: [
          _buildFilterPanel(),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : displayStudents.isEmpty
                    ? const Center(child: Text('暂无学生'))
                    : RefreshIndicator(
                        onRefresh: _reloadKeepPosition,
                        child: ListView.builder(
                          controller: _scrollController,
                          itemCount: displayStudents.length,
                          itemBuilder: (_, i) {
                            final s = displayStudents[i];
                            final classKey = _classGroupKey(s);
                            final prevClassKey =
                                i > 0 ? _classGroupKey(displayStudents[i - 1]) : null;
                            final isClassStart = i > 0 && classKey != prevClassKey;
                            final subtitleWidgets = <Widget>[
                              if (s.displayClass.isNotEmpty)
                                Text(
                                  '行政班：${s.displayClass}',
                                  style: const TextStyle(
                                      fontSize: 12, color: Colors.blueGrey),
                                ),
                              if (s.electiveClass != null &&
                                  s.electiveClass!.isNotEmpty)
                                Text(
                                  '选修班：${s.electiveClass}',
                                  style: const TextStyle(
                                      fontSize: 12, color: Colors.orange),
                                ),
                            ];
                            final titleText = (s.studentNo ?? '').trim().isEmpty
                                ? s.name
                                : '${s.name}（${s.studentNo}）';

                            return Container(
                              decoration: BoxDecoration(
                                color: classGroupColors[classKey] ?? Colors.white,
                                border: isClassStart
                                    ? const Border(
                                        top: BorderSide(
                                            color: Colors.black26, width: 2),
                                      )
                                    : null,
                              ),
                              child: ListTile(
                                leading: CircleAvatar(
                                  backgroundColor: s.isMale
                                      ? Colors.blue.shade100
                                      : Colors.pink.shade100,
                                  child: Text(
                                    s.gender ?? '?',
                                    style: TextStyle(
                                      color: s.isMale ? Colors.blue : Colors.pink,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                                title: Text(titleText),
                                subtitle: subtitleWidgets.isEmpty
                                    ? null
                                    : Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: subtitleWidgets,
                                      ),
                                isThreeLine: subtitleWidgets.length > 1,
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      tooltip: '出勤记录',
                                      icon: const Icon(
                                        Icons.fact_check_outlined,
                                        color: Colors.green,
                                      ),
                                      onPressed: () => _showAttendanceDialog(s),
                                    ),
                                    IconButton(
                                      tooltip: '编辑',
                                      icon: const Icon(
                                        Icons.edit,
                                        color: Colors.blue,
                                      ),
                                      onPressed: () => _showEditDialog(s),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}
