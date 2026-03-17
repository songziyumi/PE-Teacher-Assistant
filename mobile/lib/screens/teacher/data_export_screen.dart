import 'dart:io';
import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import '../../models/school_class.dart';
import '../../services/teacher_service.dart';

class TeacherDataExportScreen extends StatefulWidget {
  const TeacherDataExportScreen({super.key});

  @override
  State<TeacherDataExportScreen> createState() =>
      _TeacherDataExportScreenState();
}

class _TeacherDataExportScreenState extends State<TeacherDataExportScreen> {
  List<SchoolClass> _classes = [];
  int? _selectedClassId;
  bool _loading = false;
  bool _exporting = false;
  String? _exportingType;

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
            .showSnackBar(SnackBar(content: Text('加载失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _doExport(String type) async {
    setState(() { _exporting = true; _exportingType = type; });
    try {
      final today = _today();
      if (type == 'course_requests') {
        final bytes = await TeacherService.exportCourseRequests();
        await _saveAndOpen(bytes, '审批记录_$today.xlsx');
      } else if (type == 'students') {
        final bytes = await TeacherService.exportStudents(
            classId: _selectedClassId);
        await _saveAndOpen(bytes, '学生名单_$today.xlsx');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('导出失败: $e')));
      }
    } finally {
      if (mounted) setState(() { _exporting = false; _exportingType = null; });
    }
  }

  Future<void> _saveAndOpen(List<int> bytes, String filename) async {
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/$filename');
    await file.writeAsBytes(bytes);
    final result = await OpenFile.open(file.path);
    if (result.type != ResultType.done && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('无法打开文件：${result.message}')),
      );
    }
  }

  String _today() {
    final now = DateTime.now();
    return '${now.year}${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('数据导出')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // 审批记录导出
                _ExportCard(
                  icon: Icons.approval,
                  color: const Color(0xFF3498db),
                  title: '审批记录',
                  subtitle: '导出我的全部选课审批记录（申请人、课程、状态、备注）',
                  exporting: _exporting && _exportingType == 'course_requests',
                  onExport:
                      _exporting ? null : () => _doExport('course_requests'),
                ),
                const SizedBox(height: 12),
                // 学生名单导出
                Card(
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  elevation: 1,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: const Color(0xFF27ae60)
                                  .withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: const Icon(Icons.people,
                                color: Color(0xFF27ae60)),
                          ),
                          const SizedBox(width: 12),
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('学生名单',
                                    style: TextStyle(
                                        fontSize: 15,
                                        fontWeight: FontWeight.bold)),
                                Text('导出学生名单（学号、姓名、性别、年级、班级、选修班、学籍状态）',
                                    style: TextStyle(
                                        fontSize: 12, color: Colors.grey)),
                              ],
                            ),
                          ),
                        ]),
                        const SizedBox(height: 12),
                        DropdownButtonFormField<int?>(
                          value: _selectedClassId,
                          decoration: const InputDecoration(
                            labelText: '班级（可选，不选则导出全部）',
                            border: OutlineInputBorder(),
                            contentPadding: EdgeInsets.symmetric(
                                horizontal: 12, vertical: 8),
                            isDense: true,
                          ),
                          items: [
                            const DropdownMenuItem(
                                value: null, child: Text('全部班级')),
                            ..._classes.map((c) => DropdownMenuItem(
                                value: c.id, child: Text(c.displayName))),
                          ],
                          onChanged: (v) =>
                              setState(() => _selectedClassId = v),
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton.icon(
                            onPressed: _exporting
                                ? null
                                : () => _doExport('students'),
                            icon: _exporting && _exportingType == 'students'
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2, color: Colors.white),
                                  )
                                : const Icon(Icons.download),
                            label: Text(
                                _exporting && _exportingType == 'students'
                                    ? '导出中...'
                                    : '导出 Excel'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFF27ae60),
                              foregroundColor: Colors.white,
                              shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(8)),
                            ),
                          ),
                        ),
                      ],
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

class _ExportCard extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final bool exporting;
  final VoidCallback? onExport;

  const _ExportCard({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.exporting,
    required this.onExport,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(icon, color: color),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: const TextStyle(
                          fontSize: 15, fontWeight: FontWeight.bold)),
                  Text(subtitle,
                      style:
                          const TextStyle(fontSize: 12, color: Colors.grey)),
                ],
              ),
            ),
            const SizedBox(width: 8),
            ElevatedButton.icon(
              onPressed: onExport,
              icon: exporting
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.download, size: 16),
              label: Text(exporting ? '导出中' : '导出',
                  style: const TextStyle(fontSize: 13)),
              style: ElevatedButton.styleFrom(
                backgroundColor: color,
                foregroundColor: Colors.white,
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
