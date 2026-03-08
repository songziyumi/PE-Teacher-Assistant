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

  String? _gender;
  DateTime? _birthDate;
  String? _photoUrl;

  @override
  void initState() {
    super.initState();
    _loadProfile();
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
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '资料编辑',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 12),
                        DropdownButtonFormField<String?>(
                          value: _gender,
                          decoration: const InputDecoration(
                            labelText: '性别',
                            border: OutlineInputBorder(),
                          ),
                          items: const [
                            DropdownMenuItem(value: null, child: Text('未设置')),
                            DropdownMenuItem(value: '男', child: Text('男')),
                            DropdownMenuItem(value: '女', child: Text('女')),
                          ],
                          onChanged: (v) => setState(() => _gender = v),
                        ),
                        const SizedBox(height: 12),
                        InkWell(
                          onTap: _pickBirthDate,
                          child: InputDecorator(
                            decoration: const InputDecoration(
                              labelText: '出生年月',
                              border: OutlineInputBorder(),
                            ),
                            child: Text(
                              _birthDate == null
                                  ? '未设置'
                                  : '${_birthDate!.year}-${_birthDate!.month.toString().padLeft(2, '0')}-${_birthDate!.day.toString().padLeft(2, '0')}',
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _specialtyCtrl,
                          decoration: const InputDecoration(
                            labelText: '专业特长',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _emailCtrl,
                          keyboardType: TextInputType.emailAddress,
                          decoration: const InputDecoration(
                            labelText: '邮箱',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _bioCtrl,
                          maxLines: 4,
                          decoration: const InputDecoration(
                            labelText: '自我介绍',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _savingProfile ? null : _saveProfile,
                            child: _savingProfile
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Text('保存资料'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '修改密码',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _oldPwdCtrl,
                          obscureText: true,
                          decoration: const InputDecoration(
                            labelText: '当前密码',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _newPwdCtrl,
                          obscureText: true,
                          decoration: const InputDecoration(
                            labelText: '新密码',
                            hintText: '至少8位，包含字母和数字',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _confirmPwdCtrl,
                          obscureText: true,
                          decoration: const InputDecoration(
                            labelText: '确认新密码',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed:
                                _changingPassword ? null : _changePassword,
                            child: _changingPassword
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Text('更新密码'),
                          ),
                        ),
                      ],
                    ),
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
}
