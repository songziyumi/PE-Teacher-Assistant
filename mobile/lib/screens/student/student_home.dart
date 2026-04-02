import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../models/student_request_course.dart';
import '../../models/student_selection.dart';
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
  Map<String, dynamic>? _currentEvent;
  List<StudentRequestCourse> _activeCourses = [];
  List<StudentRequestCourse> _requestableCourses = [];
  List<StudentSelection> _mySelections = [];
  final Set<int> _submittingCourseIds = <int>{};
  bool _forceChangeDialogShowing = false;

  bool get _hasActiveEvent => _currentEvent != null;

  bool get _inRound1 => _currentEvent?['inRound1'] == true;

  bool get _inRound2 => _currentEvent?['inRound2'] == true;

  bool get _hasConfirmedSelection =>
      _mySelections.any((item) => item.status == 'CONFIRMED');

  bool get _hasLotteryFail =>
      _mySelections.any((item) => item.status == 'LOTTERY_FAIL');

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
        StudentService.getCurrentEvent(),
        StudentService.getCurrentCourses(),
        StudentService.getMySelections(),
        StudentService.getRequestableCourses(),
        StudentService.getUnreadMessageCount(),
      ]);
      final currentEvent = results[0] as Map<String, dynamic>?;
      final activeCourses = results[1] as List<StudentRequestCourse>;
      final mySelections = results[2] as List<StudentSelection>;
      final requestable = results[3] as Map<String, dynamic>;
      final unreadCount = results[4] as int;
      if (!mounted) return;
      setState(() {
        _currentEvent = currentEvent;
        _activeCourses = activeCourses;
        _mySelections = mySelections;
        _canRequest = requestable['canRequest'] == true;
        _reason = requestable['reason']?.toString() ?? '';
        _eventName = requestable['eventName']?.toString() ?? '';
        _requestableCourses =
            (requestable['courses'] as List).cast<StudentRequestCourse>();
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

    setState(() => _submittingCourseIds.add(course.id));
    try {
      await StudentService.requestCourse(course.id, content: content);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('申请已发送，请等待教师处理')),
      );
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('发送失败: $e')));
    } finally {
      if (mounted) {
        setState(() => _submittingCourseIds.remove(course.id));
      }
    }
  }

  Future<void> _selectCourse(StudentRequestCourse course) async {
    setState(() => _submittingCourseIds.add(course.id));
    try {
      await StudentService.selectCourse(course.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已选中《${course.name}》')),
      );
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('抢课失败: $e')));
    } finally {
      if (mounted) {
        setState(() => _submittingCourseIds.remove(course.id));
      }
    }
  }

  String _formatEventTime(dynamic value) {
    if (value == null) return '-';
    final parsed = DateTime.tryParse(value.toString());
    if (parsed == null) return value.toString();
    final text = parsed.toLocal().toString();
    return text.length >= 16 ? text.substring(0, 16) : text;
  }

  String _currentEventTitle() {
    if (_inRound2) {
      return _hasConfirmedSelection ? '第二轮进行中，您已完成选课' : '第二轮进行中';
    }
    if (_inRound1) {
      return '第一轮进行中';
    }
    final status = _currentEvent?['status']?.toString() ?? '';
    if (status == 'PROCESSING') return '第一轮抽签结算中';
    if (status == 'ROUND2') return '第二轮即将开始';
    if (status == 'ROUND1') return '第一轮即将开始';
    return '当前选课活动';
  }

  String _currentEventDescription() {
    if (_inRound2) {
      if (_hasConfirmedSelection) {
        return '您已拥有确认课程，可在下方查看各课程当前人数。';
      }
      if (_hasLotteryFail) {
        return '第一轮未中签，可在下方查看课程人数和剩余名额后参加第二轮抢课；如未参加，第二轮结束后系统会随机分配到仍有空余名额的课程。';
      }
      return '可在下方查看课程人数和剩余名额。';
    }
    if (_inRound1) {
      return '可查看当前课程人数和剩余名额变化。';
    }
    final status = _currentEvent?['status']?.toString() ?? '';
    if (status == 'PROCESSING') {
      return '系统正在结算第一轮抽签结果，请稍后刷新。';
    }
    return '可在下方查看当前活动课程。';
  }

  String _currentEventTimeLabel() {
    if (_inRound2) {
      return '截止时间：${_formatEventTime(_currentEvent?['round2End'])}';
    }
    if (_inRound1) {
      return '截止时间：${_formatEventTime(_currentEvent?['round1End'])}';
    }
    final status = _currentEvent?['status']?.toString() ?? '';
    if (status == 'ROUND2') {
      return '开始时间：${_formatEventTime(_currentEvent?['round2Start'])}';
    }
    if (status == 'ROUND1') {
      return '开始时间：${_formatEventTime(_currentEvent?['round1Start'])}';
    }
    return '';
  }

  Widget _buildCurrentEventCard() {
    final Color background = _inRound2
        ? Colors.green.shade50
        : (_inRound1 ? Colors.blue.shade50 : Colors.orange.shade50);
    final String eventName = _currentEvent?['name']?.toString() ?? '';
    final String timeLabel = _currentEventTimeLabel();

    return Card(
      color: background,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              _currentEventTitle(),
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 15,
              ),
            ),
            const SizedBox(height: 6),
            if (eventName.isNotEmpty) Text('活动：$eventName'),
            Text(_currentEventDescription()),
            if (timeLabel.isNotEmpty) Text(timeLabel),
          ],
        ),
      ),
    );
  }

  Widget _buildRequestStatusCard() {
    return Card(
      color: _canRequest ? Colors.blue.shade50 : Colors.orange.shade50,
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
    );
  }

  Widget _buildCurrentCourseCard(StudentRequestCourse course) {
    final bool submitting = _submittingCourseIds.contains(course.id);
    final bool canSelect = _inRound2 &&
        !_hasConfirmedSelection &&
        !course.confirmed &&
        course.remaining > 0 &&
        !submitting;

    String buttonText;
    if (submitting) {
      buttonText = '提交中...';
    } else if (course.confirmed) {
      buttonText = '已选中';
    } else if (_hasConfirmedSelection) {
      buttonText = '已完成选课';
    } else if (course.remaining <= 0) {
      buttonText = '名额已满';
    } else {
      buttonText = '立即抢课';
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    course.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                if (course.confirmed)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.green.shade50,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      '已选中',
                      style: TextStyle(
                        color: Colors.green.shade800,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  )
                else if (course.myPreference > 0)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.blue.shade50,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      '第${course.myPreference}志愿',
                      style: TextStyle(
                        color: Colors.blue.shade800,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 4),
            Text('教师：${course.teacherName ?? '-'}'),
            Text('人数：${course.currentCount}/${course.totalCapacity}（剩余${course.remaining}）'),
            if ((course.description ?? '').isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  course.description!,
                  style: const TextStyle(color: Colors.black87),
                ),
              ),
            if (_inRound2) ...[
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: canSelect ? () => _selectCourse(course) : null,
                  icon: submitting
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.flash_on),
                  label: Text(buttonText),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildRequestableCourseCard(StudentRequestCourse course) {
    final bool submitting = _submittingCourseIds.contains(course.id);
    return Card(
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
            Text('人数：${course.currentCount}/${course.totalCapacity}（剩余${course.remaining}）'),
            if ((course.description ?? '').isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  course.description!,
                  style: const TextStyle(color: Colors.black87),
                ),
              ),
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: (!_canRequest || submitting)
                    ? null
                    : () => _sendRequest(course),
                icon: submitting
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send),
                label: Text(submitting ? '发送中...' : '发送申请'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().user;

    return Scaffold(
      appBar: AppBar(
        title: const Text('学生选课'),
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
                      subtitle: const Text('祝你学习进步，运动快乐。'),
                    ),
                  ),
                  if (_hasActiveEvent)
                    _buildCurrentEventCard()
                  else
                    _buildRequestStatusCard(),
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
                  const SizedBox(height: 14),
                  Text(
                    _hasActiveEvent ? '当前活动课程' : '可申请课程',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 10),
                  if (_hasActiveEvent && _activeCourses.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 32),
                      child: Center(child: Text('当前暂无可查看课程')),
                    )
                  else if (!_hasActiveEvent && _requestableCourses.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 32),
                      child: Center(child: Text('暂无可申请课程')),
                    )
                  else if (_hasActiveEvent)
                    ..._activeCourses.map(_buildCurrentCourseCard)
                  else
                    ..._requestableCourses.map(_buildRequestableCourseCard),
                ],
              ),
            ),
      bottomNavigationBar: const StudentBottomNav(currentIndex: 0),
    );
  }
}
