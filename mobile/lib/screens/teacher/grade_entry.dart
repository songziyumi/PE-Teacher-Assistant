import 'package:flutter/material.dart';
import '../../models/student.dart';
import '../../models/term_grade.dart';
import '../../services/network_service.dart';
import '../../services/offline_queue_service.dart';
import '../../services/permission_cache.dart';
import '../../services/teacher_service.dart';
import '../../widgets/connectivity_banner.dart';

class GradeEntryScreen extends StatefulWidget {
  final int classId;
  final String className;
  const GradeEntryScreen({super.key, required this.classId, required this.className});

  @override
  State<GradeEntryScreen> createState() => _GradeEntryScreenState();
}

class _GradeEntryScreenState extends State<GradeEntryScreen> {
  List<Student> _students = [];
  Map<int, TermGrade> _existing = {};
  final Map<int, Map<String, TextEditingController>> _ctrls = {};

  String _academicYear = '';
  String _semester = '上学期';
  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _academicYear = now.month >= 9
        ? '${now.year}-${now.year + 1}'
        : '${now.year - 1}-${now.year}';
    _load();
  }

  @override
  void dispose() {
    for (final m in _ctrls.values) {
      for (final c in m.values) {
        c.dispose();
      }
    }
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      _students = await TeacherService.getStudents(widget.classId);
      _existing = await TeacherService.getTermGrades(widget.classId, _academicYear, _semester);
      for (final s in _students) {
        final g = _existing[s.id];
        _ctrls[s.id] = {
          'attendance': TextEditingController(text: g?.attendanceScore?.toString() ?? ''),
          'skill': TextEditingController(text: g?.skillScore?.toString() ?? ''),
          'theory': TextEditingController(text: g?.theoryScore?.toString() ?? ''),
          'remark': TextEditingController(text: g?.remark ?? ''),
        };
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  List<Map<String, dynamic>> _buildItems() {
    return _students.map((s) {
      final c = _ctrls[s.id]!;
      return {
        'studentId': s.id,
        'attendanceScore': double.tryParse(c['attendance']!.text),
        'skillScore': double.tryParse(c['skill']!.text),
        'theoryScore': double.tryParse(c['theory']!.text),
        'remark': c['remark']!.text.isEmpty ? null : c['remark']!.text,
      };
    }).toList();
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final items = _buildItems();
      if (!NetworkService.isOnline) {
        await _queueGrades(items);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('当前离线，成绩已暂存，联网后自动同步'),
            backgroundColor: Colors.orange,
          ));
        }
        return;
      }
      await TeacherService.saveTermGrades(_academicYear, _semester, items);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('成绩保存成功'), backgroundColor: Colors.green));
      }
    } catch (e) {
      if (!mounted) return;
      if (!NetworkService.isOnline) {
        await _queueGrades(_buildItems());
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('网络中断，成绩已暂存，联网后自动同步'),
            backgroundColor: Colors.orange,
          ));
        }
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('保存失败: $e'), backgroundColor: Colors.red));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _queueGrades(List<Map<String, dynamic>> items) =>
      OfflineQueueService.enqueueTermGrades(
        classId: widget.classId,
        className: widget.className,
        academicYear: _academicYear,
        semester: _semester,
        items: items,
      );

  @override
  Widget build(BuildContext context) {
    final canEdit = PermissionCache.current.termGradeEdit;
    return Scaffold(
      appBar: AppBar(
        title: Text('成绩录入 - ${widget.className}'),
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
              child: const Row(children: [
                Icon(Icons.lock_outline, size: 16, color: Colors.orange),
                SizedBox(width: 6),
                Text('管理员已禁用成绩录入功能',
                    style: TextStyle(color: Colors.orange)),
              ]),
            ),
          _buildFilterBar(),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: _students.length,
                    itemBuilder: (_, i) => _GradeCard(
                      student: _students[i],
                      ctrls: _ctrls[_students[i].id]!,
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      color: Colors.purple.shade50,
      child: Row(
        children: [
          Expanded(
            child: TextFormField(
              initialValue: _academicYear,
              decoration: const InputDecoration(
                labelText: '学年', isDense: true, border: OutlineInputBorder()),
              onChanged: (v) => _academicYear = v,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: DropdownButtonFormField<String>(
              initialValue: _semester,
              decoration: const InputDecoration(
                labelText: '学期', isDense: true, border: OutlineInputBorder()),
              items: ['上学期', '下学期']
                  .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                  .toList(),
              onChanged: (v) => setState(() => _semester = v!),
            ),
          ),
          const SizedBox(width: 8),
          ElevatedButton(
            onPressed: _load,
            style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 12)),
            child: const Text('查询'),
          ),
        ],
      ),
    );
  }
}

class _GradeCard extends StatefulWidget {
  final Student student;
  final Map<String, TextEditingController> ctrls;
  const _GradeCard({required this.student, required this.ctrls});

  @override
  State<_GradeCard> createState() => _GradeCardState();
}

class _GradeCardState extends State<_GradeCard> {
  double? _previewTotal;
  String? _previewLevel;

  void _recalc() {
    final a = double.tryParse(widget.ctrls['attendance']!.text);
    final s = double.tryParse(widget.ctrls['skill']!.text);
    final t = double.tryParse(widget.ctrls['theory']!.text);
    setState(() {
      _previewTotal = TermGrade.computeTotal(a, s, t);
      _previewLevel = TermGrade.computeLevel(_previewTotal);
    });
  }

  @override
  void initState() {
    super.initState();
    _recalc();
    widget.ctrls['attendance']!.addListener(_recalc);
    widget.ctrls['skill']!.addListener(_recalc);
    widget.ctrls['theory']!.addListener(_recalc);
  }

  static const _levelColors = {
    '优秀': Color(0xFF27ae60),
    '良好': Color(0xFF2980b9),
    '及格': Color(0xFFf39c12),
    '不及格': Color(0xFFe74c3c),
  };

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(widget.student.name,
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    if (widget.student.studentNo != null)
                      Text(widget.student.studentNo!,
                          style: const TextStyle(color: Colors.grey, fontSize: 12)),
                  ]),
                ),
                // 实时综合分预览
                if (_previewTotal != null)
                  Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                    Text('$_previewTotal',
                        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold,
                            color: Color(0xFF4a90e2))),
                    if (_previewLevel != null)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: (_levelColors[_previewLevel] ?? Colors.grey)
                              .withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(_previewLevel!,
                            style: TextStyle(
                                fontSize: 12,
                                color: _levelColors[_previewLevel] ?? Colors.grey,
                                fontWeight: FontWeight.w600)),
                      ),
                  ]),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                _ScoreField('出勤分', widget.ctrls['attendance']!),
                const SizedBox(width: 8),
                _ScoreField('技能分', widget.ctrls['skill']!),
                const SizedBox(width: 8),
                _ScoreField('理论分', widget.ctrls['theory']!),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: widget.ctrls['remark'],
              decoration: const InputDecoration(
                labelText: '备注', isDense: true, border: OutlineInputBorder()),
              style: const TextStyle(fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }
}

class _ScoreField extends StatelessWidget {
  final String label;
  final TextEditingController ctrl;
  const _ScoreField(this.label, this.ctrl);

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: TextFormField(
        controller: ctrl,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(
          labelText: label, isDense: true, border: const OutlineInputBorder(),
          contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        ),
        style: const TextStyle(fontSize: 14),
      ),
    );
  }
}
