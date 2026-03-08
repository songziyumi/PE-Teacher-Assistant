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
  bool _saving = false;
  bool _obscureOld = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;

  @override
  void dispose() {
    _oldCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final oldPassword = _oldCtrl.text;
    final newPassword = _newCtrl.text;
    final confirm = _confirmCtrl.text;

    if (oldPassword.isEmpty || newPassword.isEmpty || confirm.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请完整填写密码信息')));
      return;
    }
    if (newPassword != confirm) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('两次输入的新密码不一致')));
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
      );
      if (!mounted) return;

      await auth.refreshCurrentUser();
      if (!mounted) return;

      messenger.showSnackBar(const SnackBar(content: Text('密码修改成功')));
      router.go('/student');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('修改失败: $e')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !widget.forceChange,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('修改密码'),
          automaticallyImplyLeading: !widget.forceChange,
          actions: [
            if (widget.forceChange)
              TextButton(
                onPressed: () => context.read<AuthProvider>().logout(),
                child: const Text('退出', style: TextStyle(color: Colors.white)),
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
                  child: Text('首次登录必须先修改密码。'),
                ),
              ),
            if (widget.forceChange) const SizedBox(height: 12),
            TextField(
              controller: _oldCtrl,
              obscureText: _obscureOld,
              decoration: InputDecoration(
                labelText: '旧密码',
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
                hintText: '至少8位，且包含字母和数字',
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
            const SizedBox(height: 20),
            SizedBox(
              height: 46,
              child: ElevatedButton(
                onPressed: _saving ? null : _submit,
                child: _saving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('保存密码'),
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
