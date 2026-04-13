import 'dart:io';

import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';

import '../../models/school_class.dart';
import '../../models/student_account.dart';
import '../../services/admin_service.dart';

class StudentAccountScreen extends StatefulWidget {
  const StudentAccountScreen({super.key});

  @override
  State<StudentAccountScreen> createState() => _StudentAccountScreenState();
}

class _StudentAccountScreenState extends State<StudentAccountScreen> {
  static const _statusOptions = <MapEntry<String, String>>[
    MapEntry('', '全部状态'),
    MapEntry('UNGENERATED', '未生成'),
    MapEntry('PENDING', '未激活'),
    MapEntry('ACTIVE', '正常'),
    MapEntry('DISABLED', '已禁用'),
    MapEntry('LOCKED', '已锁定'),
  ];

  final _searchCtrl = TextEditingController();
  final Set<int> _selectedIds = {};

  List<Grade> _grades = [];
  List<SchoolClass> _allClasses = [];
  List<SchoolClass> _classes = [];
  List<StudentAccountRow> _items = [];

  bool _loading = false;
  bool _metaLoading = false;
  bool _submitting = false;
  bool _exporting = false;
  bool _batchMode = false;

  int? _gradeId;
  int? _classId;
  String _accountStatus = '';
  int _page = 0;
  int _totalPages = 0;

  @override
  void initState() {
    super.initState();
    _loadMeta();
    _load();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadMeta() async {
    setState(() => _metaLoading = true);
    try {
      final results =
          await Future.wait([AdminService.getGrades(), AdminService.getClasses()]);
      if (!mounted) return;
      final grades = results[0] as List<Grade>;
      final classes = results[1] as List<SchoolClass>;
      setState(() {
        _grades = grades;
        _allClasses = classes;
        _classes = _filterClasses(classes, _gradeId);
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载筛选项失败: $e')));
    } finally {
      if (mounted) setState(() => _metaLoading = false);
    }
  }

  List<SchoolClass> _filterClasses(List<SchoolClass> source, int? gradeId) {
    if (gradeId == null) return source;
    return source.where((item) => item.gradeId == gradeId).toList();
  }

  Future<void> _load({int page = 0}) async {
    setState(() => _loading = true);
    try {
      final data = await AdminService.getStudentAccounts(
        keyword: _searchCtrl.text.trim(),
        gradeId: _gradeId,
        classId: _classId,
        accountStatus: _accountStatus,
        page: page,
      );
      final items = (data['content'] as List? ?? const [])
          .map((e) => StudentAccountRow.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      final visibleIds = items.map((e) => e.studentId).toSet();
      if (!mounted) return;
      setState(() {
        _items = items;
        _page = (data['page'] as num?)?.toInt() ?? 0;
        _totalPages = (data['totalPages'] as num?)?.toInt() ?? 0;
        _selectedIds.removeWhere((id) => !visibleIds.contains(id));
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载学生账号失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Map<String, dynamic> _buildParams({bool selectedOnly = false}) {
    return {
      if (selectedOnly) 'studentIds': _selectedIds.toList(growable: false),
      if (!selectedOnly && _gradeId != null) 'gradeId': _gradeId,
      if (!selectedOnly && _classId != null) 'classId': _classId,
      if (!selectedOnly && _searchCtrl.text.trim().isNotEmpty)
        'keyword': _searchCtrl.text.trim(),
      if (!selectedOnly && _accountStatus.isNotEmpty) 'accountStatus': _accountStatus,
    };
  }

  bool get _busy => _submitting || _exporting;

  bool get _allVisibleSelected =>
      _items.isNotEmpty && _items.every((item) => _selectedIds.contains(item.studentId));

  void _toggleBatchMode() {
    setState(() {
      _batchMode = !_batchMode;
      _selectedIds.clear();
    });
  }

  void _toggleSelectAll() {
    setState(() {
      if (_allVisibleSelected) {
        _selectedIds.removeAll(_items.map((e) => e.studentId));
      } else {
        _selectedIds.addAll(_items.map((e) => e.studentId));
      }
    });
  }

  Future<bool> _confirm(String title, String content) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(content),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('确认'),
          ),
        ],
      ),
    );
    return result == true;
  }

  Future<void> _runBatch({
    required String title,
    required String successLabel,
    required Future<Map<String, dynamic>> Function(Map<String, dynamic>) runner,
  }) async {
    if (_selectedIds.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请先选择学生')));
      return;
    }
    final confirmed = await _confirm(title, '确认对已选 ${_selectedIds.length} 名学生执行该操作吗？');
    if (!confirmed) return;
    setState(() => _submitting = true);
    try {
      final result = await runner(_buildParams(selectedOnly: true));
      if (!mounted) return;
      await _load(page: _page);
      if (!mounted) return;
      final count = result['successCount'] ?? 0;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('$successLabel成功：$count')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('$title失败: $e')));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _export({required bool selectedOnly}) async {
    if (selectedOnly && _selectedIds.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请先选择学生')));
      return;
    }
    final confirmed = await _confirm(
      '导出学生账号',
      selectedOnly ? '确认导出已选 ${_selectedIds.length} 名学生吗？' : '确认导出当前筛选结果吗？',
    );
    if (!confirmed) return;

    setState(() => _exporting = true);
    try {
      final bytes = await AdminService.exportStudentAccounts(
        studentIds: selectedOnly ? _selectedIds.toList(growable: false) : null,
        gradeId: selectedOnly ? null : _gradeId,
        classId: selectedOnly ? null : _classId,
        keyword: selectedOnly ? '' : _searchCtrl.text.trim(),
        accountStatus: selectedOnly ? '' : _accountStatus,
      );
      await _saveAndOpen(bytes, '学生账号_${_today()}.xlsx');
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('导出成功，已尝试打开文件')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('导出失败: $e')));
    } finally {
      if (mounted) setState(() => _exporting = false);
    }
  }

  Future<void> _saveAndOpen(List<int> bytes, String filename) async {
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/$filename');
    await file.writeAsBytes(bytes, flush: true);
    final result = await OpenFile.open(file.path);
    if (result.type != ResultType.done && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('文件已生成，但无法自动打开：${result.message}')),
      );
    }
  }

  String _today() {
    final now = DateTime.now();
    final month = now.month.toString().padLeft(2, '0');
    final day = now.day.toString().padLeft(2, '0');
    return '${now.year}$month$day';
  }

  String _formatDate(DateTime? value) {
    if (value == null) return '-';
    final text = value.toLocal().toString();
    return text.length >= 16 ? text.substring(0, 16) : text;
  }

  Color _statusColor(String status) {
    switch (status) {
      case '未激活':
        return const Color(0xFFD97706);
      case '正常':
        return const Color(0xFF15803D);
      case '已禁用':
        return const Color(0xFFB91C1C);
      case '已锁定':
        return const Color(0xFF2563EB);
      default:
        return const Color(0xFF6B7280);
    }
  }

  Future<void> _showDetail(StudentAccountRow item) {
    return showModalBottomSheet<void>(
      context: context,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(item.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              Text('学号：${item.studentNo?.isNotEmpty == true ? item.studentNo : '-'}'),
              Text('班级：${item.displayClass.isNotEmpty ? item.displayClass : '-'}'),
              Text('系统账号：${item.hasAccount ? item.loginId : '-'}'),
              Text('便捷账号：${item.hasLoginAlias ? item.loginAlias : '-'}'),
              Text('状态：${item.status}'),
              Text('已激活：${item.activated ? '是' : '否'}'),
              Text('已改密：${item.passwordChanged ? '是' : '否'}'),
              Text('最后登录：${_formatDate(item.lastLoginAt)}'),
              Text('最近重置：${_formatDate(item.lastPasswordResetAt)}'),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: _batchMode ? Text('已选 ${_selectedIds.length} 名学生') : const Text('学生账号管理'),
        actions: [
          IconButton(
            onPressed: _loading ? null : () => _load(page: _page),
            icon: const Icon(Icons.refresh),
          ),
          if (_batchMode) ...[
            TextButton(
              onPressed: _items.isEmpty ? null : _toggleSelectAll,
              child: Text(_allVisibleSelected ? '取消全选' : '全选',
                  style: const TextStyle(color: Colors.white)),
            ),
            TextButton(
              onPressed: _busy ? null : _toggleBatchMode,
              child: const Text('退出', style: TextStyle(color: Colors.white)),
            ),
          ] else
            IconButton(
              onPressed: _busy ? null : _toggleBatchMode,
              icon: const Icon(Icons.checklist),
            ),
        ],
      ),
      body: Column(
        children: [
            Card(
              margin: const EdgeInsets.fromLTRB(12, 12, 12, 8),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: [
                    TextField(
                      controller: _searchCtrl,
                      decoration: const InputDecoration(
                        hintText: '搜索姓名或学号',
                        prefixIcon: Icon(Icons.search),
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      onSubmitted: (_) => _load(),
                    ),
                    const SizedBox(height: 10),
                    DropdownButtonFormField<int?>(
                      initialValue: _gradeId,
                      decoration: const InputDecoration(
                        labelText: '年级',
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      items: [
                        const DropdownMenuItem<int?>(value: null, child: Text('全部年级')),
                        ..._grades.map((g) => DropdownMenuItem<int?>(value: g.id, child: Text(g.name))),
                      ],
                      onChanged: _metaLoading
                          ? null
                          : (value) {
                              setState(() {
                                _gradeId = value;
                                _classId = null;
                                _classes = _filterClasses(_allClasses, value);
                              });
                              _load();
                            },
                    ),
                    const SizedBox(height: 10),
                    DropdownButtonFormField<int?>(
                      initialValue: _classId,
                      decoration: const InputDecoration(
                        labelText: '班级',
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      items: [
                        const DropdownMenuItem<int?>(value: null, child: Text('全部班级')),
                        ..._classes.map((c) => DropdownMenuItem<int?>(value: c.id, child: Text(c.displayName))),
                      ],
                      onChanged: (value) {
                        setState(() => _classId = value);
                        _load();
                      },
                    ),
                    const SizedBox(height: 10),
                    DropdownButtonFormField<String>(
                      initialValue: _accountStatus,
                      decoration: const InputDecoration(
                        labelText: '账号状态',
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      items: _statusOptions
                          .map((e) => DropdownMenuItem<String>(value: e.key, child: Text(e.value)))
                          .toList(),
                      onChanged: (value) {
                        setState(() => _accountStatus = value ?? '');
                        _load();
                      },
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: _loading
                                ? null
                                : () {
                                    _searchCtrl.clear();
                                    setState(() {
                                      _gradeId = null;
                                      _classId = null;
                                      _accountStatus = '';
                                      _classes = _allClasses;
                                    });
                                    _load();
                                  },
                            child: const Text('重置'),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _loading ? null : () => _load(),
                            child: const Text('搜索'),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _items.isEmpty
                      ? ListView(
                          children: const [
                            SizedBox(height: 120),
                            Center(child: Text('暂无学生账号数据')),
                          ],
                        )
                      : ListView.builder(
                          padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                          itemCount: _items.length,
                          itemBuilder: (_, index) {
                            final item = _items[index];
                            final selected = _selectedIds.contains(item.studentId);
                            final statusColor = _statusColor(item.status);
                            return Card(
                              margin: const EdgeInsets.only(bottom: 10),
                              child: ListTile(
                                leading: _batchMode
                                    ? Checkbox(
                                        value: selected,
                                        onChanged: _busy
                                            ? null
                                            : (_) => setState(() {
                                                  if (selected) {
                                                    _selectedIds.remove(item.studentId);
                                                  } else {
                                                    _selectedIds.add(item.studentId);
                                                  }
                                                }),
                                      )
                                    : null,
                                title: Row(
                                  children: [
                                    Expanded(
                                      child: Text(item.name,
                                          style: const TextStyle(fontWeight: FontWeight.w600)),
                                    ),
                                    Container(
                                      padding:
                                          const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                      decoration: BoxDecoration(
                                        color: statusColor.withValues(alpha: 0.12),
                                        borderRadius: BorderRadius.circular(999),
                                      ),
                                      child: Text(item.status,
                                          style: TextStyle(
                                            fontSize: 12,
                                            color: statusColor,
                                            fontWeight: FontWeight.w600,
                                          )),
                                    ),
                                  ],
                                ),
                                subtitle: Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        '${item.studentNo?.isNotEmpty == true ? item.studentNo : '-'} · ${item.displayClass.isNotEmpty ? item.displayClass : '-'}',
                                      ),
                                      Text('系统账号：${item.hasAccount ? item.loginId : '未生成'}'),
                                      Text('便捷账号：${item.hasLoginAlias ? item.loginAlias : '未绑定'}'),
                                      Text('激活：${item.activated ? '是' : '否'}  改密：${item.passwordChanged ? '是' : '否'}'),
                                      Text('最后登录：${_formatDate(item.lastLoginAt)}'),
                                    ],
                                  ),
                                ),
                                isThreeLine: false,
                                onTap: _batchMode
                                    ? () => setState(() {
                                          if (selected) {
                                            _selectedIds.remove(item.studentId);
                                          } else {
                                            _selectedIds.add(item.studentId);
                                          }
                                        })
                                    : () => _showDetail(item),
                              ),
                            );
                          },
                        ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
              child: _batchMode
                  ? Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        FilledButton.tonalIcon(
                          onPressed: _busy
                              ? null
                              : () => _runBatch(
                                    title: '批量生成账号',
                                    successLabel: '生成账号',
                                    runner: AdminService.batchGenerateStudentAccounts,
                                  ),
                          icon: const Icon(Icons.person_add_alt_1, size: 18),
                          label: const Text('生成'),
                        ),
                        FilledButton.tonalIcon(
                          onPressed: _busy
                              ? null
                              : () => _runBatch(
                                    title: '批量重置密码',
                                    successLabel: '重置密码',
                                    runner: AdminService.batchResetStudentAccounts,
                                  ),
                          icon: const Icon(Icons.lock_reset, size: 18),
                          label: const Text('重置'),
                        ),
                        FilledButton.tonalIcon(
                          onPressed: _busy
                              ? null
                              : () => _runBatch(
                                    title: '批量启用账号',
                                    successLabel: '启用账号',
                                    runner: AdminService.batchEnableStudentAccounts,
                                  ),
                          icon: const Icon(Icons.check_circle_outline, size: 18),
                          label: const Text('启用'),
                        ),
                        FilledButton.tonalIcon(
                          onPressed: _busy
                              ? null
                              : () => _runBatch(
                                    title: '批量禁用账号',
                                    successLabel: '禁用账号',
                                    runner: AdminService.batchDisableStudentAccounts,
                                  ),
                          icon: const Icon(Icons.block, size: 18),
                          label: const Text('禁用'),
                        ),
                        FilledButton.tonalIcon(
                          onPressed: _busy ? null : () => _export(selectedOnly: true),
                          icon: _exporting
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(Icons.download, size: 18),
                          label: const Text('导出'),
                        ),
                      ],
                    )
                  : SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: _busy ? null : () => _export(selectedOnly: false),
                        icon: _exporting
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.download),
                        label: Text(_exporting ? '导出中...' : '导出当前筛选结果'),
                      ),
                    ),
            ),
            if (_totalPages > 1)
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      onPressed: _page > 0 && !_loading ? () => _load(page: _page - 1) : null,
                      icon: const Icon(Icons.chevron_left),
                    ),
                    Text('${_page + 1} / $_totalPages'),
                    IconButton(
                      onPressed: _page < _totalPages - 1 && !_loading
                          ? () => _load(page: _page + 1)
                          : null,
                      icon: const Icon(Icons.chevron_right),
                    ),
                  ],
                ),
              ),
        ],
      ),
    );
  }
}
