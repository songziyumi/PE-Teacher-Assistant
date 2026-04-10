import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../models/student.dart';
import '../../services/network_service.dart';
import '../../services/offline_queue_service.dart';
import '../../services/permission_cache.dart';
import '../../services/teacher_service.dart';
import '../../widgets/connectivity_banner.dart';

class AttendanceScreen extends StatefulWidget {
  final int classId;
  final String className;

  const AttendanceScreen({
    super.key,
    required this.classId,
    required this.className,
  });

  @override
  State<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends State<AttendanceScreen> {
  static const Color _classGroupColorA = Color(0xFFDDEEFF);
  static const Color _classGroupColorB = Color(0xFFFFE2C2);
  static const String _presentStatus = '出勤';
  static const String _absentStatus = '缺勤';
  static const String _leaveStatus = '请假';

  List<Student> _students = [];
  Map<int, String> _statusMap = {};
  DateTime _date = DateTime.now();
  bool _loading = true;
  bool _saving = false;
  String? _selectedStatusFilter;

  @override
  void initState() {
    super.initState();
    _load();
  }

  String get _dateStr => DateFormat('yyyy-MM-dd').format(_date);

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      _students = await TeacherService.getStudents(widget.classId);
      _sortStudentsForGroupedAttendance();
      final existing = await TeacherService.getAttendance(
        widget.classId,
        _dateStr,
      );
      _statusMap = {
        for (final student in _students)
          student.id: existing[student.id] ?? _presentStatus,
      };
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('加载失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime.now(),
    );
    if (picked != null && picked != _date) {
      _date = picked;
      await _load();
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      if (!NetworkService.isOnline) {
        await _queueAttendance();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('当前离线，考勤已暂存，联网后自动同步'),
              backgroundColor: Colors.orange,
            ),
          );
        }
        return;
      }
      await TeacherService.saveAttendance(widget.classId, _dateStr, _statusMap);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('考勤保存成功'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (!mounted) return;
      if (!NetworkService.isOnline) {
        await _queueAttendance();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('网络中断，考勤已暂存，联网后自动同步'),
              backgroundColor: Colors.orange,
            ),
          );
        }
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('保存失败: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _queueAttendance() => OfflineQueueService.enqueueAttendance(
    classId: widget.classId,
    className: widget.className,
    date: _dateStr,
    statusMap: _statusMap,
  );

  void _sortStudentsForGroupedAttendance() {
    final distinctClasses = _students
        .map(_classLabel)
        .where((label) => label.isNotEmpty)
        .toSet();
    if (distinctClasses.length <= 1) {
      return;
    }

    _students.sort((left, right) {
      final classCompare = _classLabel(left).compareTo(_classLabel(right));
      if (classCompare != 0) {
        return classCompare;
      }
      final studentNoCompare = (left.studentNo ?? '').compareTo(
        right.studentNo ?? '',
      );
      if (studentNoCompare != 0) {
        return studentNoCompare;
      }
      return left.name.compareTo(right.name);
    });
  }

  String _classLabel(Student student) => student.displayClass.trim();

  List<Student> _buildVisibleStudents() {
    if (_selectedStatusFilter == null) {
      return _students;
    }
    return _students
        .where((student) => _statusMap[student.id] == _selectedStatusFilter)
        .toList();
  }

  Map<int, Color> _buildRowColors(List<Student> students) {
    final colors = <int, Color>{};
    String? previousClass;
    bool useFirst = true;

    for (final student in students) {
      final currentClass = _classLabel(student);
      if (currentClass != previousClass) {
        if (previousClass != null) {
          useFirst = !useFirst;
        }
        previousClass = currentClass;
      }
      colors[student.id] = useFirst ? _classGroupColorA : _classGroupColorB;
    }
    return colors;
  }

  void _toggleStatusFilter(String status) {
    setState(() {
      _selectedStatusFilter = _selectedStatusFilter == status ? null : status;
    });
  }

  @override
  Widget build(BuildContext context) {
    final canEdit = PermissionCache.current.attendanceEdit;
    final visibleStudents = _buildVisibleStudents();
    final rowColors = _buildRowColors(visibleStudents);

    return Scaffold(
      appBar: AppBar(
        title: Text('考勤 - ${widget.className}'),
        actions: [
          TextButton.icon(
            onPressed: _saving || !canEdit ? null : _save,
            icon: const Icon(Icons.save, color: Colors.white),
            label: const Text('保存', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
      body: Column(
        children: [
          const ConnectivityBanner(),
          if (!canEdit)
            Container(
              width: double.infinity,
              color: Colors.orange.shade100,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: const Row(
                children: [
                  Icon(Icons.lock_outline, size: 16, color: Colors.orange),
                  SizedBox(width: 6),
                  Text(
                    '管理员已禁用考勤录入功能',
                    style: TextStyle(color: Colors.orange),
                  ),
                ],
              ),
            ),
          InkWell(
            onTap: _pickDate,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              color: Colors.blue.shade50,
              child: Row(
                children: [
                  const Icon(Icons.calendar_today, color: Color(0xFF4a90e2)),
                  const SizedBox(width: 8),
                  Text(
                    _dateStr,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF4a90e2),
                    ),
                  ),
                  const Spacer(),
                  const Icon(Icons.arrow_drop_down, color: Color(0xFF4a90e2)),
                ],
              ),
            ),
          ),
          if (!_loading) _buildStats(),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : visibleStudents.isEmpty
                ? Center(
                    child: Text(
                      _selectedStatusFilter == null
                          ? '暂无学生'
                          : '暂无${_selectedStatusFilter!}学生',
                    ),
                  )
                : ListView.builder(
                    itemCount: visibleStudents.length,
                    itemBuilder: (_, i) {
                      final student = visibleStudents[i];
                      return _StudentAttendanceRow(
                        student: student,
                        status: _statusMap[student.id] ?? _presentStatus,
                        backgroundColor:
                            rowColors[student.id] ?? _classGroupColorA,
                        onChanged: (value) => setState(() {
                          _statusMap[student.id] = value;
                        }),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildStats() {
    final present = _statusMap.values.where((v) => v == _presentStatus).length;
    final absent = _statusMap.values.where((v) => v == _absentStatus).length;
    final leave = _statusMap.values.where((v) => v == _leaveStatus).length;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _StatChip(
            _presentStatus,
            present,
            const Color(0xFF27ae60),
            selected: _selectedStatusFilter == _presentStatus,
            onTap: () => _toggleStatusFilter(_presentStatus),
          ),
          _StatChip(
            _absentStatus,
            absent,
            const Color(0xFFe74c3c),
            selected: _selectedStatusFilter == _absentStatus,
            onTap: () => _toggleStatusFilter(_absentStatus),
          ),
          _StatChip(
            _leaveStatus,
            leave,
            const Color(0xFF2980b9),
            selected: _selectedStatusFilter == _leaveStatus,
            onTap: () => _toggleStatusFilter(_leaveStatus),
          ),
        ],
      ),
    );
  }
}

class _StatChip extends StatelessWidget {
  final String label;
  final int count;
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  const _StatChip(
    this.label,
    this.count,
    this.color, {
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: selected ? color.withOpacity(0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: selected ? color : Colors.transparent),
        ),
        child: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 4),
            Text(
              '$label $count',
              style: TextStyle(color: color, fontWeight: FontWeight.bold),
            ),
          ],
        ),
      ),
    );
  }
}

class _StudentAttendanceRow extends StatelessWidget {
  final Student student;
  final String status;
  final Color backgroundColor;
  final ValueChanged<String> onChanged;

  const _StudentAttendanceRow({
    required this.student,
    required this.status,
    required this.backgroundColor,
    required this.onChanged,
  });

  static const _statuses = ['出勤', '缺勤', '请假'];
  static const _colors = {
    '出勤': Color(0xFF27ae60),
    '缺勤': Color(0xFFe74c3c),
    '请假': Color(0xFF2980b9),
  };

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0x0F172A0F)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      student.name,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    if ((student.gender ?? '').trim().isNotEmpty) ...[
                      const SizedBox(width: 6),
                      Text(
                        student.gender!,
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ],
                ),
                if (student.displayClass.isNotEmpty)
                  Text(
                    student.displayClass,
                    style: const TextStyle(color: Colors.grey, fontSize: 12),
                  ),
              ],
            ),
          ),
          Row(
            children: _statuses.map((value) {
              final selected = status == value;
              final color = _colors[value]!;
              return GestureDetector(
                onTap: () => onChanged(value),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  margin: const EdgeInsets.only(left: 6),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: selected ? color : Colors.transparent,
                    border: Border.all(color: color),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Text(
                    value,
                    style: TextStyle(
                      fontSize: 13,
                      color: selected ? Colors.white : color,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}
