import 'package:flutter/material.dart';
import '../../services/admin_service.dart';
import '../../models/term_grade.dart';

class GradeListScreen extends StatefulWidget {
  const GradeListScreen({super.key});

  @override
  State<GradeListScreen> createState() => _GradeListScreenState();
}

class _GradeListScreenState extends State<GradeListScreen> {
  final _searchCtrl = TextEditingController();
  List<TermGrade> _list = [];
  int _page = 0, _totalPages = 0;
  bool _loading = false;
  String _academicYear = '';
  String _semester = '';

  static const _levelColors = {
    '优秀': Color(0xFF27ae60),
    '良好': Color(0xFF2980b9),
    '及格': Color(0xFFf39c12),
    '不及格': Color(0xFFe74c3c),
  };

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _academicYear = now.month >= 9 ? '${now.year}-${now.year + 1}' : '${now.year - 1}-${now.year}';
    _load();
  }

  Future<void> _load({int page = 0}) async {
    setState(() => _loading = true);
    try {
      final data = await AdminService.getTermGrades(
        academicYear: _academicYear, semester: _semester,
        keyword: _searchCtrl.text, page: page,
      );
      final content = (data['content'] as List)
          .map((e) => TermGrade.fromJson(e as Map<String, dynamic>))
          .toList();
      setState(() {
        _list = content;
        _page = data['page'] as int;
        _totalPages = data['totalPages'] as int;
      });
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _delete(int id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('确认删除'),
        content: const Text('确定要删除这条成绩记录吗？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (ok == true) {
      await AdminService.deleteTermGrade(id);
      _load(page: _page);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('期末成绩管理')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _searchCtrl,
                        decoration: const InputDecoration(
                          hintText: '姓名/学号', prefixIcon: Icon(Icons.search),
                          border: OutlineInputBorder(), isDense: true,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton(onPressed: () => _load(), child: const Text('搜索')),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
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
                        initialValue: _semester.isEmpty ? null : _semester,
                        decoration: const InputDecoration(
                          labelText: '学期', isDense: true, border: OutlineInputBorder()),
                        items: [
                          const DropdownMenuItem(value: null, child: Text('全部学期')),
                          ...['上学期', '下学期'].map((s) => DropdownMenuItem(value: s, child: Text(s))),
                        ],
                        onChanged: (v) => setState(() => _semester = v ?? ''),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _list.isEmpty
                    ? const Center(child: Text('暂无成绩记录'))
                    : ListView.builder(
                        itemCount: _list.length,
                        itemBuilder: (_, i) {
                          final g = _list[i];
                          final levelColor = _levelColors[g.level] ?? Colors.grey;
                          return Card(
                            margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                            child: ListTile(
                              title: Text('${g.studentName ?? ''} ${g.studentNo ?? ''}',
                                  style: const TextStyle(fontWeight: FontWeight.w600)),
                              subtitle: Text(
                                '${g.academicYear ?? ''} ${g.semester ?? ''}\n'
                                '出勤: ${g.attendanceScore ?? '-'} · 技能: ${g.skillScore ?? '-'} · 理论: ${g.theoryScore ?? '-'} → 综合: ${g.totalScore ?? '-'}',
                              ),
                              isThreeLine: true,
                              trailing: g.level != null
                                  ? Column(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                          decoration: BoxDecoration(
                                            color: levelColor.withValues(
                                              alpha: 0.15,
                                            ),
                                            borderRadius: BorderRadius.circular(8),
                                          ),
                                          child: Text(g.level!,
                                              style: TextStyle(color: levelColor, fontWeight: FontWeight.bold)),
                                        ),
                                      ],
                                    )
                                  : null,
                              onLongPress: g.id != null ? () => _delete(g.id!) : null,
                            ),
                          );
                        },
                      ),
          ),
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
                    onPressed: _page < _totalPages - 1 ? () => _load(page: _page + 1) : null,
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
