import 'package:flutter/material.dart';

import '../../services/teacher_service.dart';

class TeacherAccountSecurityScreen extends StatefulWidget {
  const TeacherAccountSecurityScreen({super.key});

  @override
  State<TeacherAccountSecurityScreen> createState() =>
      _TeacherAccountSecurityScreenState();
}

class _TeacherAccountSecurityScreenState
    extends State<TeacherAccountSecurityScreen> {
  final _emailCtrl = TextEditingController();

  bool _loading = true;
  bool _sending = false;
  bool _toggling = false;
  bool _emailVerified = false;
  bool _emailNotifyEnabled = true;
  String _currentEmail = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final data = await TeacherService.getProfile();
      if (!mounted) {
        return;
      }
      final email = data['email']?.toString() ?? '';
      setState(() {
        _currentEmail = email;
        _emailCtrl.text = email;
        _emailVerified = data['emailVerified'] == true;
        _emailNotifyEnabled = data['emailNotifyEnabled'] != false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('加载邮箱信息失败：$error')),
      );
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _requestEmailBind() async {
    final email = _emailCtrl.text.trim();
    if (email.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请先填写邮箱地址')),
      );
      return;
    }
    setState(() => _sending = true);
    try {
      await TeacherService.requestEmailBind(email: email);
      if (!mounted) {
        return;
      }
      await _load();
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('验证邮件已发送，请前往邮箱完成验证')),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('发送验证邮件失败：$error')),
      );
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  Future<void> _toggleNotify(bool enabled) async {
    setState(() => _toggling = true);
    try {
      await TeacherService.updateEmailNotifyEnabled(enabled: enabled);
      if (!mounted) {
        return;
      }
      setState(() => _emailNotifyEnabled = enabled);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('邮箱通知设置已更新')),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('更新邮箱通知失败：$error')),
      );
    } finally {
      if (mounted) {
        setState(() => _toggling = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('邮箱安全')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '绑定邮箱',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _emailCtrl,
                          keyboardType: TextInputType.emailAddress,
                          decoration: const InputDecoration(
                            labelText: '邮箱地址',
                            hintText: 'name@example.com',
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _currentEmail.isEmpty
                              ? '当前未绑定邮箱'
                              : (_emailVerified
                                  ? '当前邮箱：$_currentEmail（已验证）'
                                  : '当前邮箱：$_currentEmail（待验证）'),
                          style: TextStyle(
                            fontSize: 12,
                            color: _emailVerified ? Colors.green : Colors.orange,
                          ),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          '用于忘记密码等账号安全通知；本地联调时请把邮件域名替换为当前测试地址。',
                          style: TextStyle(fontSize: 12, color: Colors.grey),
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _sending ? null : _requestEmailBind,
                            child: _sending
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : Text(_currentEmail.isEmpty ? '绑定邮箱' : '发送验证邮件'),
                          ),
                        ),
                        const SizedBox(height: 8),
                        SwitchListTile(
                          value: _emailNotifyEnabled,
                          onChanged: _toggling ? null : _toggleNotify,
                          contentPadding: EdgeInsets.zero,
                          title: const Text('接收邮箱通知'),
                          subtitle: const Text('用于忘记密码等账号安全通知'),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}
