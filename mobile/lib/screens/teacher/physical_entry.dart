import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/student.dart';
import '../../models/physical_test.dart';
import '../../services/teacher_service.dart';

class PhysicalEntryScreen extends StatefulWidget {
  final int classId;
  final String className;
  const PhysicalEntryScreen({super.key, required this.classId, required this.className});

  @override
  State<PhysicalEntryScreen> createState() => _PhysicalEntryScreenState();
}

class _PhysicalEntryScreenState extends State<PhysicalEntryScreen> {
  List<Student> _students = [];
  Map<int, PhysicalTest> _existing = {};
  // 每个学生对应一组 TextEditingController
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
      _existing = await TeacherService.getPhysicalTests(widget.classId, _academicYear, _semester);
      // 初始化控制器
      for (final s in _students) {
        final pt = _existing[s.id];
        _ctrls[s.id] = {
          'height': TextEditingController(text: pt?.height?.toString() ?? ''),
          'weight': TextEditingController(text: pt?.weight?.toString() ?? ''),
          'lungCapacity': TextEditingController(text: pt?.lungCapacity?.toString() ?? ''),
          'sprint50m': TextEditingController(text: pt?.sprint50m?.toString() ?? ''),
          'sitReach': TextEditingController(text: pt?.sitReach?.toString() ?? ''),
          'standingJump': TextEditingController(text: pt?.standingJump?.toString() ?? ''),
          'pullUps': TextEditingController(text: pt?.pullUps?.toString() ?? ''),
          'sitUps': TextEditingController(text: pt?.sitUps?.toString() ?? ''),
          'run800m': TextEditingController(text: pt?.run800m?.toString() ?? ''),
          'run1000m': TextEditingController(text: pt?.run1000m?.toString() ?? ''),
          'remark': TextEditingController(text: pt?.remark ?? ''),
        };
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final items = <Map<String, dynamic>>[];
      for (final s in _students) {
        final c = _ctrls[s.id]!;
        items.add({
          'studentId': s.id,
          'academicYear': _academicYear,
          'semester': _semester,
          'testDate': DateFormat('yyyy-MM-dd').format(DateTime.now()),
          'height': double.tryParse(c['height']!.text),
          'weight': double.tryParse(c['weight']!.text),
          'lungCapacity': int.tryParse(c['lungCapacity']!.text),
          'sprint50m': double.tryParse(c['sprint50m']!.text),
          'sitReach': double.tryParse(c['sitReach']!.text),
          'standingJump': double.tryParse(c['standingJump']!.text),
          'pullUps': s.isMale ? int.tryParse(c['pullUps']!.text) : null,
          'sitUps': s.isFemale ? int.tryParse(c['sitUps']!.text) : null,
          'run800m': s.isFemale ? double.tryParse(c['run800m']!.text) : null,
          'run1000m': s.isMale ? double.tryParse(c['run1000m']!.text) : null,
          'remark': c['remark']!.text.isEmpty ? null : c['remark']!.text,
        });
      }
      await TeacherService.savePhysicalTests(items);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('体测数据保存成功'), backgroundColor: Colors.green));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('保存失败: $e'), backgroundColor: Colors.red));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('体测录入 - ${widget.className}'),
        actions: [
          TextButton.icon(
            onPressed: _saving ? null : _save,
            icon: const Icon(Icons.save, color: Colors.white),
            label: const Text('保存', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
      body: Column(
        children: [
          // 学年学期选择
          _buildFilterBar(),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: _students.length,
                    itemBuilder: (_, i) => _StudentPhysicalCard(
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
      color: Colors.blue.shade50,
      child: Row(
        children: [
          Expanded(
            child: TextFormField(
              initialValue: _academicYear,
              decoration: const InputDecoration(
                labelText: '学年', isDense: true, border: OutlineInputBorder(),
              ),
              onChanged: (v) => _academicYear = v,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: DropdownButtonFormField<String>(
              initialValue: _semester,
              decoration: const InputDecoration(
                labelText: '学期', isDense: true, border: OutlineInputBorder(),
              ),
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

class _StudentPhysicalCard extends StatelessWidget {
  final Student student;
  final Map<String, TextEditingController> ctrls;

  const _StudentPhysicalCard({required this.student, required this.ctrls});

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
                Text(student.name,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: student.isMale ? Colors.blue.shade100 : Colors.pink.shade100,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(student.gender ?? '',
                      style: TextStyle(
                          fontSize: 11,
                          color: student.isMale ? Colors.blue.shade800 : Colors.pink.shade800)),
                ),
                if (student.studentNo != null) ...[
                  const SizedBox(width: 8),
                  Text(student.studentNo!, style: const TextStyle(color: Colors.grey, fontSize: 12)),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _Field('身高(cm)', ctrls['height']!),
                _Field('体重(kg)', ctrls['weight']!),
                _Field('肺活量(mL)', ctrls['lungCapacity']!, isInt: true),
                _Field('50米跑(秒)', ctrls['sprint50m']!),
                _Field('坐位体前屈(cm)', ctrls['sitReach']!),
                _Field('立定跳远(cm)', ctrls['standingJump']!),
                if (student.isMale) _Field('引体向上(个)', ctrls['pullUps']!, isInt: true),
                if (student.isFemale) _Field('仰卧起坐(个/分)', ctrls['sitUps']!, isInt: true),
                if (student.isMale) _Field('1000米(秒)', ctrls['run1000m']!),
                if (student.isFemale) _Field('800米(秒)', ctrls['run800m']!),
                _Field('备注', ctrls['remark']!, isText: true),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  final String label;
  final TextEditingController ctrl;
  final bool isInt;
  final bool isText;

  const _Field(this.label, this.ctrl, {this.isInt = false, this.isText = false});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: isText ? double.infinity : 130,
      child: TextFormField(
        controller: ctrl,
        keyboardType: isText
            ? TextInputType.text
            : isInt
                ? TextInputType.number
                : const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(
          labelText: label,
          isDense: true,
          border: const OutlineInputBorder(),
          contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        ),
        style: const TextStyle(fontSize: 14),
      ),
    );
  }
}
