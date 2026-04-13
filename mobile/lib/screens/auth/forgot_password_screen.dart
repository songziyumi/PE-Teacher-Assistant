import 'package:flutter/material.dart';

import '../../services/auth_service.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _accountCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();

  bool _submitting = false;
  bool _submitted = false;
  String? _message;

  Future<void> _submit() async {
    final account = _accountCtrl.text.trim();
    final email = _emailCtrl.text.trim();

    if (account.isEmpty || email.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请填写账号和已验证邮箱')),
      );
      return;
    }

    setState(() => _submitting = true);
    try {
      final message = await AuthService.requestPasswordReset(
        account: account,
        email: email,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _submitted = true;
        _message = message;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('提交失败：$error')),
      );
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  void dispose() {
    _accountCtrl.dispose();
    _emailCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('忘记密码')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '请输入账号和已验证邮箱。若信息匹配，系统会发送一封密码重置邮件。',
                    style: TextStyle(height: 1.5),
                  ),
                  const SizedBox(height: 16),
                  if (_submitted) ...[
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.green.shade50,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.green.shade200),
                      ),
                      child: Text(
                        _message ?? '如信息匹配，重置邮件已发送',
                        style: TextStyle(color: Colors.green.shade700),
                      ),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      '请到邮箱中打开重置链接。本地联调时，请把邮件中的域名替换为当前测试地址后再打开。',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const SizedBox(height: 16),
                    OutlinedButton(
                      onPressed: () {
                        setState(() {
                          _submitted = false;
                          _message = null;
                        });
                      },
                      child: const Text('重新申请'),
                    ),
                  ] else ...[
                    TextField(
                      controller: _accountCtrl,
                      decoration: const InputDecoration(
                        labelText: '账号',
                        hintText: '请输入登录账号或便捷账号',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _emailCtrl,
                      keyboardType: TextInputType.emailAddress,
                      decoration: const InputDecoration(
                        labelText: '已验证邮箱',
                        hintText: 'name@example.com',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      '为保护账号安全，系统不会提示账号是否存在或邮箱是否匹配。',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const SizedBox(height: 20),
                    SizedBox(
                      height: 46,
                      child: ElevatedButton(
                        onPressed: _submitting ? null : _submit,
                        child: _submitting
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('发送重置邮件'),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
