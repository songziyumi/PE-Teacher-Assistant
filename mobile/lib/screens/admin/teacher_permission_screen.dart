import 'package:flutter/material.dart';
import '../../models/teacher_permission.dart';
import '../../services/admin_service.dart';

class TeacherPermissionScreen extends StatefulWidget {
  const TeacherPermissionScreen({super.key});

  @override
  State<TeacherPermissionScreen> createState() =>
      _TeacherPermissionScreenState();
}

class _TeacherPermissionScreenState extends State<TeacherPermissionScreen> {
  TeacherPermission _perm = TeacherPermission.defaultAll;
  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      _perm = await AdminService.getTeacherPermissions();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加载失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      _perm = await AdminService.updateTeacherPermissions(_perm);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
              content: Text('权限设置已保存'), backgroundColor: Colors.green),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('保存失败: $e')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _toggle(TeacherPermission updated) {
    setState(() => _perm = updated);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('教师功能权限'),
        actions: [
          TextButton(
            onPressed: _saving || _loading ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child:
                        CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Text('保存', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const Text(
                  '控制本校教师在手机端可使用的功能和可编辑的字段。\n开启 = 允许，关闭 = 禁止。',
                  style: TextStyle(fontSize: 13, color: Colors.grey),
                ),
                const SizedBox(height: 16),
                _SectionCard(
                  title: '学生信息编辑',
                  items: [
                    _PermItem(
                      label: '姓名',
                      desc: '允许教师修改学生姓名',
                      value: _perm.editStudentName,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(editStudentName: v)),
                    ),
                    _PermItem(
                      label: '性别',
                      desc: '允许教师修改学生性别',
                      value: _perm.editStudentGender,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(editStudentGender: v)),
                    ),
                    _PermItem(
                      label: '学号',
                      desc: '允许教师修改学生学号',
                      value: _perm.editStudentNo,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(editStudentNo: v)),
                    ),
                    _PermItem(
                      label: '学籍状态',
                      desc: '允许教师修改学籍状态（在籍/休学/毕业等）',
                      value: _perm.editStudentStatus,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(editStudentStatus: v)),
                    ),
                    _PermItem(
                      label: '行政班',
                      desc: '允许教师调整学生所在行政班',
                      value: _perm.editStudentClass,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(editStudentClass: v)),
                    ),
                    _PermItem(
                      label: '选修班',
                      desc: '允许教师分配/修改学生选修班',
                      value: _perm.editStudentElectiveClass,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(editStudentElectiveClass: v)),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _SectionCard(
                  title: '功能模块',
                  items: [
                    _PermItem(
                      label: '考勤录入',
                      desc: '允许教师在手机端录入考勤',
                      value: _perm.attendanceEdit,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(attendanceEdit: v)),
                    ),
                    _PermItem(
                      label: '体测录入',
                      desc: '允许教师在手机端录入体质健康测试数据',
                      value: _perm.physicalTestEdit,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(physicalTestEdit: v)),
                    ),
                    _PermItem(
                      label: '成绩录入',
                      desc: '允许教师在手机端录入期末成绩',
                      value: _perm.termGradeEdit,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(termGradeEdit: v)),
                    ),
                    _PermItem(
                      label: '批量操作',
                      desc: '允许教师对学生进行批量修改（批量改学籍、选修班等）',
                      value: _perm.batchOperation,
                      onChanged: (v) =>
                          _toggle(_perm.copyWith(batchOperation: v)),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 48,
                  child: ElevatedButton(
                    onPressed: _saving ? null : _save,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF27ae60),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                    ),
                    child: _saving
                        ? const CircularProgressIndicator(color: Colors.white)
                        : const Text('保存设置', style: TextStyle(fontSize: 16)),
                  ),
                ),
              ],
            ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final String title;
  final List<_PermItem> items;

  const _SectionCard({required this.title, required this.items});

  @override
  Widget build(BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 1,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Text(
              title,
              style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF2c3e50)),
            ),
          ),
          const Divider(height: 1),
          ...items,
        ],
      ),
    );
  }
}

class _PermItem extends StatelessWidget {
  final String label;
  final String desc;
  final bool value;
  final ValueChanged<bool> onChanged;

  const _PermItem({
    required this.label,
    required this.desc,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      title: Text(label, style: const TextStyle(fontSize: 15)),
      subtitle: Text(desc,
          style: const TextStyle(fontSize: 12, color: Colors.grey)),
      value: value,
      onChanged: onChanged,
      activeColor: const Color(0xFF27ae60),
    );
  }
}
