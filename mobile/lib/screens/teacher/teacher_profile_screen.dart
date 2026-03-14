import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../../config/api_config.dart';
import '../../providers/auth_provider.dart';
import '../../services/teacher_service.dart';
import '../../widgets/teacher_bottom_nav.dart';

class TeacherProfileScreen extends StatefulWidget {
  const TeacherProfileScreen({super.key});

  @override
  State<TeacherProfileScreen> createState() => _TeacherProfileScreenState();
}

class _TeacherProfileScreenState extends State<TeacherProfileScreen> {
  final _specialtyCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _bioCtrl = TextEditingController();
  final _oldPwdCtrl = TextEditingController();
  final _newPwdCtrl = TextEditingController();
  final _confirmPwdCtrl = TextEditingController();

  bool _loading = true;
  bool _savingProfile = false;
  bool _changingPassword = false;
  bool _uploadingPhoto = false;
  bool _editingProfile = false;
  bool _showPasswordForm = false;

  String? _gender;
  DateTime? _birthDate;
  String? _photoUrl;

  // 编辑模式快照，用于取消时还原
  String? _snapGender;
  DateTime? _snapBirthDate;
  String _snapSpecialty = '';
  String _snapEmail = '';
  String _snapBio = '';

  Map<String, dynamic>? _stats;

  @override
  void initState() {
    super.initState();
    _loadProfile();
    _loadStats();
  }

  Future<void> _loadStats() async {
    try {
      final data = await TeacherService.getProfileStats();
      if (mounted) setState(() => _stats = data);
    } catch (_) {
      // 统计加载失败静默忽略，不影响主页面
    }
  }

  @override
  void dispose() {
    _specialtyCtrl.dispose();
    _emailCtrl.dispose();
    _bioCtrl.dispose();
    _oldPwdCtrl.dispose();
    _newPwdCtrl.dispose();
    _confirmPwdCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadProfile() async {
    setState(() => _loading = true);
    try {
      final data = await TeacherService.getProfile();
      if (!mounted) return;
      _gender = data['gender']?.toString();
      final birth = data['birthDate']?.toString();
      _birthDate =
          (birth == null || birth.isEmpty) ? null : DateTime.tryParse(birth);
      _specialtyCtrl.text = data['specialty']?.toString() ?? '';
      _emailCtrl.text = data['email']?.toString() ?? '';
      _bioCtrl.text = data['bio']?.toString() ?? '';
      _photoUrl = data['photoUrl']?.toString();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载个人资料失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickAndUploadPhoto() async {
    final picker = ImagePicker();
    final image = await picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (image == null) return;

    setState(() => _uploadingPhoto = true);
    try {
      final url = await TeacherService.uploadProfilePhoto(image.path);
      if (!mounted) return;
      setState(() => _photoUrl = url);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('头像上传成功')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('头像上传失败: $e')));
    } finally {
      if (mounted) setState(() => _uploadingPhoto = false);
    }
  }

  void _enterEditMode() {
    setState(() {
      _snapGender = _gender;
      _snapBirthDate = _birthDate;
      _snapSpecialty = _specialtyCtrl.text;
      _snapEmail = _emailCtrl.text;
      _snapBio = _bioCtrl.text;
      _editingProfile = true;
    });
  }

  void _cancelEdit() {
    setState(() {
      _gender = _snapGender;
      _birthDate = _snapBirthDate;
      _specialtyCtrl.text = _snapSpecialty;
      _emailCtrl.text = _snapEmail;
      _bioCtrl.text = _snapBio;
      _editingProfile = false;
    });
  }

  Future<void> _saveProfile() async {
    setState(() => _savingProfile = true);
    try {
      await TeacherService.updateProfile(
        gender: _gender,
        birthDate: _birthDate == null
            ? null
            : '${_birthDate!.year.toString().padLeft(4, '0')}-${_birthDate!.month.toString().padLeft(2, '0')}-${_birthDate!.day.toString().padLeft(2, '0')}',
        specialty: _specialtyCtrl.text.trim(),
        email: _emailCtrl.text.trim(),
        bio: _bioCtrl.text.trim(),
      );
      if (!mounted) return;
      setState(() => _editingProfile = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('资料已保存')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('保存失败: $e')));
    } finally {
      if (mounted) setState(() => _savingProfile = false);
    }
  }

  Future<void> _changePassword() async {
    final oldPassword = _oldPwdCtrl.text;
    final newPassword = _newPwdCtrl.text;
    final confirmPassword = _confirmPwdCtrl.text;

    if (oldPassword.isEmpty || newPassword.isEmpty || confirmPassword.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请完整填写密码信息')));
      return;
    }
    if (newPassword != confirmPassword) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('两次输入的新密码不一致')));
      return;
    }

    setState(() => _changingPassword = true);
    try {
      await TeacherService.changePassword(
        oldPassword: oldPassword,
        newPassword: newPassword,
      );
      if (!mounted) return;
      _oldPwdCtrl.clear();
      _newPwdCtrl.clear();
      _confirmPwdCtrl.clear();
      setState(() => _showPasswordForm = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('密码修改成功')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('密码修改失败: $e')));
    } finally {
      if (mounted) setState(() => _changingPassword = false);
    }
  }

  Future<void> _pickBirthDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _birthDate ?? DateTime(now.year - 25, 1, 1),
      firstDate: DateTime(1950, 1, 1),
      lastDate: now,
    );
    if (picked != null) {
      setState(() => _birthDate = picked);
    }
  }

  String? _resolvedPhotoUrl() {
    if (_photoUrl == null || _photoUrl!.trim().isEmpty) return null;
    if (_photoUrl!.startsWith('http://') || _photoUrl!.startsWith('https://')) {
      return _photoUrl;
    }
    return '${ApiConfig.baseUrl}${_photoUrl!.startsWith('/') ? '' : '/'}$_photoUrl';
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().user;
    final photoUrl = _resolvedPhotoUrl();

    return Scaffold(
      appBar: AppBar(title: const Text('个人主页')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      children: [
                        Stack(
                          children: [
                            CircleAvatar(
                              radius: 42,
                              backgroundImage: photoUrl != null
                                  ? NetworkImage(photoUrl)
                                  : null,
                              child: photoUrl == null
                                  ? Text(
                                      (user?.name ?? '师').isNotEmpty
                                          ? (user?.name ?? '师').substring(0, 1)
                                          : '师',
                                      style: const TextStyle(fontSize: 28),
                                    )
                                  : null,
                            ),
                            Positioned(
                              right: -4,
                              bottom: -4,
                              child: IconButton.filledTonal(
                                onPressed: _uploadingPhoto
                                    ? null
                                    : _pickAndUploadPhoto,
                                icon: _uploadingPhoto
                                    ? const SizedBox(
                                        width: 14,
                                        height: 14,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const Icon(Icons.photo_camera),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Text(
                          '欢迎${user?.name ?? ''}老师',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          user?.schoolName ?? '',
                          style: const TextStyle(color: Colors.grey),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                if (_stats != null) _buildStatsCard(),
                if (_stats != null) ...[
                  const SizedBox(height: 12),
                  _buildActivitiesCard(),
                ],
                const SizedBox(height: 12),
                // 个人资料卡：查看模式 / 编辑模式
                Card(
                  child: _editingProfile
                      ? Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(children: [
                                const Text('编辑资料',
                                    style: TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold)),
                                const Spacer(),
                                TextButton(
                                    onPressed: _savingProfile ? null : _cancelEdit,
                                    child: const Text('取消')),
                              ]),
                              const SizedBox(height: 12),
                              DropdownButtonFormField<String?>(
                                initialValue: _gender,
                                decoration: const InputDecoration(
                                    labelText: '性别',
                                    border: OutlineInputBorder()),
                                items: const [
                                  DropdownMenuItem(
                                      value: null, child: Text('未设置')),
                                  DropdownMenuItem(
                                      value: '男', child: Text('男')),
                                  DropdownMenuItem(
                                      value: '女', child: Text('女')),
                                ],
                                onChanged: (v) =>
                                    setState(() => _gender = v),
                              ),
                              const SizedBox(height: 12),
                              InkWell(
                                onTap: _pickBirthDate,
                                child: InputDecorator(
                                  decoration: const InputDecoration(
                                      labelText: '出生年月',
                                      border: OutlineInputBorder()),
                                  child: Text(_birthDate == null
                                      ? '未设置'
                                      : '${_birthDate!.year}-${_birthDate!.month.toString().padLeft(2, '0')}-${_birthDate!.day.toString().padLeft(2, '0')}'),
                                ),
                              ),
                              const SizedBox(height: 12),
                              TextField(
                                controller: _specialtyCtrl,
                                decoration: const InputDecoration(
                                    labelText: '专业特长',
                                    border: OutlineInputBorder()),
                              ),
                              const SizedBox(height: 12),
                              TextField(
                                controller: _emailCtrl,
                                keyboardType: TextInputType.emailAddress,
                                decoration: const InputDecoration(
                                    labelText: '邮箱',
                                    border: OutlineInputBorder()),
                              ),
                              const SizedBox(height: 12),
                              TextField(
                                controller: _bioCtrl,
                                maxLines: 4,
                                decoration: const InputDecoration(
                                    labelText: '自我介绍',
                                    border: OutlineInputBorder()),
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: ElevatedButton(
                                  onPressed:
                                      _savingProfile ? null : _saveProfile,
                                  child: _savingProfile
                                      ? const SizedBox(
                                          width: 20,
                                          height: 20,
                                          child: CircularProgressIndicator(
                                              strokeWidth: 2))
                                      : const Text('保存'),
                                ),
                              ),
                            ],
                          ),
                        )
                      : Column(
                          children: [
                            ListTile(
                              title: const Text('个人资料',
                                  style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold)),
                              trailing: TextButton.icon(
                                onPressed: _enterEditMode,
                                icon: const Icon(Icons.edit, size: 16),
                                label: const Text('编辑'),
                              ),
                            ),
                            const Divider(height: 1),
                            _infoRow('性别', _gender ?? '未设置'),
                            _infoRow(
                                '出生年月',
                                _birthDate == null
                                    ? '未设置'
                                    : '${_birthDate!.year}-${_birthDate!.month.toString().padLeft(2, '0')}-${_birthDate!.day.toString().padLeft(2, '0')}'),
                            _infoRow('专业特长',
                                _specialtyCtrl.text.isEmpty
                                    ? '未设置'
                                    : _specialtyCtrl.text),
                            _infoRow('邮箱',
                                _emailCtrl.text.isEmpty
                                    ? '未设置'
                                    : _emailCtrl.text),
                            _infoRow('自我介绍',
                                _bioCtrl.text.isEmpty
                                    ? '未设置'
                                    : _bioCtrl.text),
                            const SizedBox(height: 8),
                          ],
                        ),
                ),
                const SizedBox(height: 12),
                // 修改密码卡：折叠 / 展开
                Card(
                  child: _showPasswordForm
                      ? Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(children: [
                                const Text('修改密码',
                                    style: TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold)),
                                const Spacer(),
                                TextButton(
                                  onPressed: _changingPassword
                                      ? null
                                      : () {
                                          _oldPwdCtrl.clear();
                                          _newPwdCtrl.clear();
                                          _confirmPwdCtrl.clear();
                                          setState(() =>
                                              _showPasswordForm = false);
                                        },
                                  child: const Text('取消'),
                                ),
                              ]),
                              const SizedBox(height: 12),
                              TextField(
                                controller: _oldPwdCtrl,
                                obscureText: true,
                                decoration: const InputDecoration(
                                    labelText: '当前密码',
                                    border: OutlineInputBorder()),
                              ),
                              const SizedBox(height: 12),
                              TextField(
                                controller: _newPwdCtrl,
                                obscureText: true,
                                decoration: const InputDecoration(
                                    labelText: '新密码',
                                    hintText: '至少8位，包含字母和数字',
                                    border: OutlineInputBorder()),
                              ),
                              const SizedBox(height: 12),
                              TextField(
                                controller: _confirmPwdCtrl,
                                obscureText: true,
                                decoration: const InputDecoration(
                                    labelText: '确认新密码',
                                    border: OutlineInputBorder()),
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: ElevatedButton(
                                  onPressed: _changingPassword
                                      ? null
                                      : _changePassword,
                                  child: _changingPassword
                                      ? const SizedBox(
                                          width: 20,
                                          height: 20,
                                          child: CircularProgressIndicator(
                                              strokeWidth: 2))
                                      : const Text('更新密码'),
                                ),
                              ),
                            ],
                          ),
                        )
                      : ListTile(
                          leading: const Icon(Icons.lock_outline,
                              color: Colors.grey),
                          title: const Text('修改密码'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () =>
                              setState(() => _showPasswordForm = true),
                        ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  height: 44,
                  child: OutlinedButton.icon(
                    onPressed: () => context.read<AuthProvider>().logout(),
                    icon: const Icon(Icons.logout),
                    label: const Text('退出登录'),
                  ),
                ),
              ],
            ),
      bottomNavigationBar: const TeacherBottomNav(currentIndex: 3),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 72,
            child: Text(label,
                style: const TextStyle(color: Colors.grey, fontSize: 13)),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontSize: 13)),
          ),
        ],
      ),
    );
  }

  Widget _buildStatsCard() {
    final s = _stats!;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('教学概况',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Row(
              children: [
                _StatItem(
                  icon: Icons.class_,
                  color: const Color(0xFF4a90e2),
                  label: '带班数',
                  value: '${s['classCount'] ?? 0}',
                  unit: '个',
                ),
                _StatItem(
                  icon: Icons.how_to_reg,
                  color: const Color(0xFF27ae60),
                  label: '本月考勤',
                  value: '${s['monthlyAttendanceCount'] ?? 0}',
                  unit: '次',
                ),
                _StatItem(
                  icon: Icons.pending_actions,
                  color: const Color(0xFFe67e22),
                  label: '待审批',
                  value: '${s['pendingRequestCount'] ?? 0}',
                  unit: '条',
                ),
                _StatItem(
                  icon: Icons.check_circle_outline,
                  color: const Color(0xFF8e44ad),
                  label: '已处理',
                  value: '${s['processedRequestCount'] ?? 0}',
                  unit: '条',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActivitiesCard() {
    final activities =
        (_stats!['recentActivities'] as List?) ?? const [];
    if (activities.isEmpty) return const SizedBox.shrink();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('最近操作',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            ...activities.map((item) {
              final m = Map<String, dynamic>.from(item as Map);
              final isApprove = m['action'] == 'APPROVE';
              final timeStr = (m['time']?.toString() ?? '').length >= 16
                  ? m['time'].toString().substring(0, 16).replaceAll('T', ' ')
                  : m['time']?.toString() ?? '';
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    Icon(
                      isApprove ? Icons.check_circle : Icons.cancel,
                      size: 18,
                      color: isApprove
                          ? const Color(0xFF27ae60)
                          : const Color(0xFFe74c3c),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${isApprove ? "同意" : "拒绝"}了 ${m['studentName'] ?? '-'} 申请${m['courseName'] != null ? ' ${m['courseName']}' : ''}',
                            style: const TextStyle(fontSize: 13),
                          ),
                          if (m['remark'] != null &&
                              m['remark'].toString().isNotEmpty)
                            Text(m['remark'].toString(),
                                style: const TextStyle(
                                    fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                    ),
                    Text(timeStr,
                        style: const TextStyle(
                            fontSize: 11, color: Colors.grey)),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String label;
  final String value;
  final String unit;

  const _StatItem({
    required this.icon,
    required this.color,
    required this.label,
    required this.value,
    required this.unit,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: color, size: 20),
          ),
          const SizedBox(height: 4),
          RichText(
            text: TextSpan(
              children: [
                TextSpan(
                  text: value,
                  style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: color),
                ),
                TextSpan(
                  text: unit,
                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ],
            ),
          ),
          Text(label,
              style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ],
      ),
    );
  }
}
