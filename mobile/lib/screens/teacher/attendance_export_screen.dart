import 'dart:io';
import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import '../../models/school_class.dart';
import '../../services/teacher_service.dart';

class TeacherAttendanceExportScreen extends StatefulWidget {
  const TeacherAttendanceExportScreen({super.key});

  @override
  State<TeacherAttendanceExportScreen> createState() =>
      _TeacherAttendanceExportScreenState();
}

class _TeacherAttendanceExportScreenState
    extends State<TeacherAttendanceExportScreen> {
  List<SchoolClass> _classes = [];
  int? _selectedClassId;
  String? _selectedStatus;
  DateTime _startDate = DateTime.now();
  DateTime? _endDate;
  bool _loading = false;
  bool _exporting = false;

  static const _statusOptions = [
    _StatusOption(label: '全部状态', value: null),
    _StatusOption(label: '出勤', value: '出勤'),
    _StatusOption(label: '缺勤', value: '缺勤'),
    _StatusOption(label: '请假', value: '请假'),
  ];

  @override
  void initState() {
    super.initState();
    _loadClasses();
  }

  Future<void> _loadClasses() async {
    setState(() => _loading = true);
    try {
      _classes = await TeacherService.getClasses();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加载班级失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickDate({required bool isStart}) async {
    final initial = isStart ? _startDate : (_endDate ?? _startDate);
    final first = isStart ? DateTime(2020) : _startDate;
    final last = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: initial.isAfter(last) ? last : initial,
      firstDate: first,
      lastDate: last,
    );
    if (picked == null) return;
    setState(() {
      if (isStart) {
        _startDate = picked;
        if (_endDate != null && _endDate!.isBefore(picked)) _endDate = picked;
      } else {
        _endDate = picked;
      }
    });
  }

  Future<void> _export() async {
    setState(() => _exporting = true);
    try {
      final start = _formatDate(_startDate);
      final end = _endDate != null ? _formatDate(_endDate!) : null;
      final bytes = await TeacherService.exportAttendance(
        startDate: start,
        endDate: end,
        classId: _selectedClassId,
        status: _selectedStatus,
      );
      final dir = await getTemporaryDirectory();
      final suffix = end != null && end != start ? '_$end' : '';
      final filename = '考勤记录_$start$suffix.xlsx';
      final file = File('${dir.path}/$filename');
      await file.writeAsBytes(bytes);
      final result = await OpenFile.open(file.path);
      if (result.type != ResultType.done && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('无法打开文件：${result.message}')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('导出失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _exporting = false);
    }
  }

  String _formatDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('导出考勤记录')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  elevation: 1,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '筛选条件',
                          style: TextStyle(
                              fontSize: 15, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 16),
                        // 班级选择
                        DropdownButtonFormField<int?>(
                          initialValue: _selectedClassId,
                          decoration: const InputDecoration(
                            labelText: '班级（可选，不选则导出全部）',
                            border: OutlineInputBorder(),
                            contentPadding: EdgeInsets.symmetric(
                                horizontal: 12, vertical: 10),
                          ),
                          items: [
                            const DropdownMenuItem(
                              value: null,
                              child: Text('全部班级'),
                            ),
                            ..._classes.map((c) => DropdownMenuItem(
                                  value: c.id,
                                  child: Text(c.displayName),
                                )),
                          ],
                          onChanged: (v) =>
                              setState(() => _selectedClassId = v),
                        ),
                        const SizedBox(height: 12),
                        // 考勤状态选择
                        DropdownButtonFormField<String?>(
                          initialValue: _selectedStatus,
                          decoration: const InputDecoration(
                            labelText: '考勤状态（可选）',
                            border: OutlineInputBorder(),
                            contentPadding: EdgeInsets.symmetric(
                                horizontal: 12, vertical: 10),
                          ),
                          items: _statusOptions
                              .map((o) => DropdownMenuItem(
                                    value: o.value,
                                    child: Text(o.label),
                                  ))
                              .toList(),
                          onChanged: (v) =>
                              setState(() => _selectedStatus = v),
                        ),
                        const SizedBox(height: 16),
                        // 开始日期
                        _DatePickerRow(
                          label: '开始日期',
                          date: _startDate,
                          onTap: () => _pickDate(isStart: true),
                        ),
                        const SizedBox(height: 12),
                        // 结束日期
                        _DatePickerRow(
                          label: '结束日期（可选，不选则只导出开始日）',
                          date: _endDate,
                          onTap: () => _pickDate(isStart: false),
                          onClear: _endDate != null
                              ? () => setState(() => _endDate = null)
                              : null,
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 48,
                  child: ElevatedButton.icon(
                    onPressed: _exporting ? null : _export,
                    icon: _exporting
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.download),
                    label: Text(_exporting ? '导出中...' : '导出 Excel'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF27ae60),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  '提示：导出后将自动打开文件，请确保设备已安装 Excel 或兼容应用。',
                  style: TextStyle(fontSize: 12, color: Colors.grey),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
    );
  }
}

class _DatePickerRow extends StatelessWidget {
  final String label;
  final DateTime? date;
  final VoidCallback onTap;
  final VoidCallback? onClear;

  const _DatePickerRow({
    required this.label,
    required this.date,
    required this.onTap,
    this.onClear,
  });

  String _fmt(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          border: Border.all(color: Colors.grey.shade400),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            const Icon(Icons.calendar_today, size: 18, color: Colors.grey),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label,
                      style: const TextStyle(
                          fontSize: 12, color: Colors.grey)),
                  const SizedBox(height: 2),
                  Text(
                    date != null ? _fmt(date!) : '点击选择日期',
                    style: TextStyle(
                      fontSize: 15,
                      color: date != null
                          ? Colors.black87
                          : Colors.grey.shade500,
                    ),
                  ),
                ],
              ),
            ),
            if (onClear != null)
              GestureDetector(
                onTap: onClear,
                child: const Icon(Icons.close, size: 18, color: Colors.grey),
              ),
          ],
        ),
      ),
    );
  }
}

class _StatusOption {
  final String label;
  final String? value;
  const _StatusOption({required this.label, required this.value});
}
