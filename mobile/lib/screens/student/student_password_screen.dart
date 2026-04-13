import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../providers/auth_provider.dart';
import '../../services/student_service.dart';
import '../../widgets/student_bottom_nav.dart';

class StudentPasswordScreen extends StatefulWidget {
  final bool forceChange;

  const StudentPasswordScreen({super.key, this.forceChange = false});

  @override
  State<StudentPasswordScreen> createState() => _StudentPasswordScreenState();
}

class _StudentPasswordScreenState extends State<StudentPasswordScreen> {
  final _oldCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  final _aliasCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();

  bool _saving = false;
  bool _loadingSecurity = true;
  bool _sendingEmailBind = false;
  bool _togglingNotify = false;
  bool _obscureOld = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;

  bool _emailVerified = false;
  bool _emailNotifyEnabled = true;
  String _currentEmail = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadAccountSecurity();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final alias = context.read<AuthProvider>().user?.loginAlias ?? '';
    if (_aliasCtrl.text.isEmpty && alias.isNotEmpty) {
      _aliasCtrl.text = alias;
    }
  }

  @override
  void dispose() {
    _oldCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    _aliasCtrl.dispose();
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadAccountSecurity() async {
    setState(() => _loadingSecurity = true);
    try {
      final data = await StudentService.getAccountSecurity();
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
        setState(() => _loadingSecurity = false);
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
    setState(() => _sendingEmailBind = true);
    try {
      await StudentService.requestEmailBind(email: email);
      if (!mounted) {
        return;
      }
      await _loadAccountSecurity();
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
        SnackBar(content: Text('邮箱绑定失败：$error')),
      );
    } finally {
      if (mounted) {
        setState(() => _sendingEmailBind = false);
      }
    }
  }

  Future<void> _toggleEmailNotify(bool enabled) async {
    setState(() => _togglingNotify = true);
    try {
      await StudentService.updateEmailNotifyEnabled(enabled: enabled);
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
        setState(() => _togglingNotify = false);
      }
    }
  }

  Future<void> _submit() async {
    final oldPassword = _oldCtrl.text;
    final newPassword = _newCtrl.text;
    final confirmPassword = _confirmCtrl.text;
    final loginAlias = _aliasCtrl.text.trim();
    final existingAlias = context.read<AuthProvider>().user?.loginAlias ?? '';

    if (oldPassword.isEmpty || newPassword.isEmpty || confirmPassword.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请完整填写密码信息')),
      );
      return;
    }
    if (newPassword != confirmPassword) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('两次输入的新密码不一致')),
      );
      return;
    }
    if (widget.forceChange && loginAlias.isEmpty && existingAlias.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请先设置便捷账号')),
      );
      return;
    }
    if (loginAlias.isNotEmpty &&
        !RegExp(r'^[A-Za-z0-9]{4,20}$').hasMatch(loginAlias)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('便捷账号需为 4-20 位字母或数字')),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      final messenger = ScaffoldMessenger.of(context);
      final router = GoRouter.of(context);
      final auth = context.read<AuthProvider>();

      await StudentService.changePassword(
        oldPassword: oldPassword,
        newPassword: newPassword,
        loginAlias: loginAlias,
      );
      if (!mounted) {
        return;
      }

      await auth.refreshCurrentUser();
      if (!mounted) {
        return;
      }

      messenger.showSnackBar(
        const SnackBar(content: Text('密码修改成功，便捷账号已保存')),
      );
      router.go('/student');
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('修改失败：$error')),
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final currentAccount = auth.user?.username ?? '';
    final currentAlias = auth.user?.loginAlias ?? '';

    return PopScope(
      canPop: !widget.forceChange,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('账号安全'),
          automaticallyImplyLeading: !widget.forceChange,
          actions: [
            if (widget.forceChange)
              TextButton(
                onPressed: () => context.read<AuthProvider>().logout(),
                child: const Text(
                  '退出登录',
                  style: TextStyle(color: Colors.white),
                ),
              ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (widget.forceChange)
              Card(
                color: Colors.orange.shade50,
                child: const Padding(
                  padding: EdgeInsets.all(12),
                  child: Text('首次登录需先修改密码，并设置一个更好记的便捷账号。'),
                ),
              ),
            if (widget.forceChange) const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '登录设置',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      initialValue: currentAccount,
                      readOnly: true,
                      decoration: const InputDecoration(
                        labelText: '当前系统账号',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _aliasCtrl,
                      decoration: InputDecoration(
                        labelText: '便捷账号',
                        hintText: currentAlias.isEmpty ? '例如 zhangsan01' : null,
                        helperText: '4-20 位字母或数字，后续可用于学生登录',
                        border: const OutlineInputBorder(),
                      ),
                    ),
                    if (currentAlias.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Text(
                        '当前已绑定便捷账号：$currentAlias',
                        style: const TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '邮箱绑定',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 12),
                    if (_loadingSecurity)
                      const Center(child: CircularProgressIndicator())
                    else ...[
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
                        '点击“发送验证邮件”后，请到邮箱中完成验证；本地联调时请把邮件域名替换为当前测试地址。',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          onPressed: _sendingEmailBind ? null : _requestEmailBind,
                          child: _sendingEmailBind
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : Text(_currentEmail.isEmpty ? '绑定邮箱' : '发送验证邮件'),
                        ),
                      ),
                      const SizedBox(height: 8),
                      SwitchListTile(
                        value: _emailNotifyEnabled,
                        onChanged: _togglingNotify ? null : _toggleEmailNotify,
                        title: const Text('接收邮箱通知'),
                        subtitle: const Text('用于忘记密码等账号安全通知'),
                        contentPadding: EdgeInsets.zero,
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
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
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _oldCtrl,
                      obscureText: _obscureOld,
                      decoration: InputDecoration(
                        labelText: '当前密码',
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          onPressed: () => setState(() => _obscureOld = !_obscureOld),
                          icon: Icon(
                            _obscureOld ? Icons.visibility : Icons.visibility_off,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _newCtrl,
                      obscureText: _obscureNew,
                      decoration: InputDecoration(
                        labelText: '新密码',
                        hintText: '不少于 8 位，且包含字母和数字',
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          onPressed: () => setState(() => _obscureNew = !_obscureNew),
                          icon: Icon(
                            _obscureNew ? Icons.visibility : Icons.visibility_off,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _confirmCtrl,
                      obscureText: _obscureConfirm,
                      decoration: InputDecoration(
                        labelText: '确认新密码',
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          onPressed: () =>
                              setState(() => _obscureConfirm = !_obscureConfirm),
                          icon: Icon(
                            _obscureConfirm ? Icons.visibility : Icons.visibility_off,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      '密码规则：不少于 8 位，必须同时包含字母和数字。',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const SizedBox(height: 20),
                    SizedBox(
                      width: double.infinity,
                      height: 46,
                      child: ElevatedButton(
                        onPressed: _saving ? null : _submit,
                        child: _saving
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('保存'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
        bottomNavigationBar:
            widget.forceChange ? null : const StudentBottomNav(currentIndex: 3),
      ),
    );
  }
}
