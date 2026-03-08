import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../models/student_request_course.dart';
import '../../providers/auth_provider.dart';
import '../../services/student_service.dart';
import '../../widgets/student_bottom_nav.dart';

class StudentHome extends StatefulWidget {
  const StudentHome({super.key});

  @override
  State<StudentHome> createState() => _StudentHomeState();
}

class _StudentHomeState extends State<StudentHome> {
  bool _loading = true;
  bool _canRequest = false;
  String _reason = '';
  String _eventName = '';
  int _unreadMessageCount = 0;
  List<StudentRequestCourse> _courses = [];
  final Set<int> _sendingIds = <int>{};
  bool _forceChangeDialogShowing = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _ensureForceChangePassword();
  }

  void _ensureForceChangePassword() {
    final mustChange =
        context.read<AuthProvider>().user?.mustChangePassword ?? false;
    if (!mustChange || _forceChangeDialogShowing) return;
    _forceChangeDialogShowing = true;

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final action = await showDialog<String>(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          title: const Text('请先修改密码'),
          content: const Text('首次登录必须修改密码后才能继续使用。'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, 'logout'),
              child: const Text('退出登录'),
            ),
            ElevatedButton(
              onPressed: () => Navigator.pop(ctx, 'change'),
              child: const Text('去修改'),
            ),
          ],
        ),
      );

      if (!mounted) return;
      if (action == 'logout') {
        await context.read<AuthProvider>().logout();
        return;
      }
      await context.push('/student/password?force=true');
      _forceChangeDialogShowing = false;
      if (mounted) {
        _ensureForceChangePassword();
      }
    });
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final results = await Future.wait<dynamic>([
        StudentService.getRequestableCourses(),
        StudentService.getUnreadMessageCount(),
      ]);
      final result = results[0] as Map<String, dynamic>;
      final unreadCount = results[1] as int;
      if (!mounted) return;
      setState(() {
        _canRequest = result['canRequest'] == true;
        _reason = result['reason']?.toString() ?? '';
        _eventName = result['eventName']?.toString() ?? '';
        _courses = (result['courses'] as List).cast<StudentRequestCourse>();
        _unreadMessageCount = unreadCount;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载失败: $e')));
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _sendRequest(StudentRequestCourse course) async {
    final contentCtrl = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('申请加入《${course.name}》'),
        content: TextField(
          controller: contentCtrl,
          maxLines: 4,
          decoration: const InputDecoration(
            hintText: '可选：填写申请说明',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('发送申请'),
          ),
        ],
      ),
    );

    if (confirmed != true) {
      contentCtrl.dispose();
      return;
    }

    final content = contentCtrl.text.trim();
    contentCtrl.dispose();

    setState(() => _sendingIds.add(course.id));
    try {
      await StudentService.requestCourse(course.id, content: content);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('申请已发送，请等待教师处理')));
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('发送失败: $e')));
    } finally {
      if (mounted) {
        setState(() => _sendingIds.remove(course.id));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().user;

    return Scaffold(
      appBar: AppBar(
        title: const Text('学生选课申请'),
        actions: [
          IconButton(
            tooltip: '消息中心',
            onPressed: () => context.push('/student/messages'),
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(Icons.mail),
                if (_unreadMessageCount > 0)
                  Positioned(
                    right: -6,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      constraints:
                          const BoxConstraints(minWidth: 14, minHeight: 14),
                      child: Text(
                        _unreadMessageCount > 99
                            ? '99+'
                            : '$_unreadMessageCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<AuthProvider>().logout(),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (user?.schoolName != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text(
                        user!.schoolName!,
                        style: const TextStyle(
                          fontSize: 13,
                          color: Colors.grey,
                        ),
                      ),
                    ),
                  Card(
                    margin: const EdgeInsets.only(bottom: 10),
                    color: Colors.blue.shade50,
                    child: ListTile(
                      leading:
                          const Icon(Icons.waving_hand, color: Colors.blue),
                      title: Text(
                        '欢迎${(user?.name ?? '').isEmpty ? '' : user!.name}同学',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      subtitle: const Text('祝你学习进步，运动快乐'),
                    ),
                  ),
                  Card(
                    color: _canRequest
                        ? Colors.blue.shade50
                        : Colors.orange.shade50,
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _canRequest ? '当前可发送第三轮申请' : '当前不可发送申请',
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                            ),
                          ),
                          const SizedBox(height: 6),
                          if (_eventName.isNotEmpty) Text('活动：$_eventName'),
                          if (_reason.isNotEmpty) Text('说明：$_reason'),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.book_outlined),
                      title: const Text('我的选课'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('/student/my-courses'),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.mail_outline),
                      title: const Text('消息中心'),
                      subtitle: Text('未读消息：$_unreadMessageCount'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('/student/messages'),
                    ),
                  ),
                  const SizedBox(height: 10),
                  if (_courses.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 40),
                      child: Center(child: Text('暂无可申请课程')),
                    )
                  else
                    ..._courses.map(
                      (course) => Card(
                        margin: const EdgeInsets.only(bottom: 10),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                course.name,
                                style: const TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text('教师：${course.teacherName ?? '-'}'),
                              Text(
                                '人数：${course.currentCount}/${course.totalCapacity}（剩余${course.remaining}）',
                              ),
                              if ((course.description ?? '').isNotEmpty)
                                Padding(
                                  padding: const EdgeInsets.only(top: 4),
                                  child: Text(
                                    course.description!,
                                    style: const TextStyle(
                                      color: Colors.black87,
                                    ),
                                  ),
                                ),
                              const SizedBox(height: 10),
                              SizedBox(
                                width: double.infinity,
                                child: ElevatedButton.icon(
                                  onPressed: (!_canRequest ||
                                          _sendingIds.contains(course.id))
                                      ? null
                                      : () => _sendRequest(course),
                                  icon: _sendingIds.contains(course.id)
                                      ? const SizedBox(
                                          width: 14,
                                          height: 14,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                          ),
                                        )
                                      : const Icon(Icons.send),
                                  label: Text(
                                    _sendingIds.contains(course.id)
                                        ? '发送中...'
                                        : '发送申请',
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
      bottomNavigationBar: const StudentBottomNav(currentIndex: 0),
    );
  }
}
