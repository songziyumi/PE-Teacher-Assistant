import 'package:flutter/material.dart';
import '../models/user.dart';
import '../services/auth_service.dart';
import '../services/notification_service.dart';

class AuthProvider extends ChangeNotifier {
  UserModel? _user;
  bool _loading = true;

  UserModel? get user => _user;
  bool get loading => _loading;
  bool get isLoggedIn => _user != null;
  bool get isAdmin => _user?.isAdmin ?? false;
  bool get isStudent => _user?.isStudent ?? false;

  Future<void> tryAutoLogin() async {
    _loading = true;
    notifyListeners();
    _user = await AuthService.tryAutoLogin();
    // 自动登录成功时也启动后台通知
    if (_user != null && !(_user?.isAdmin ?? false)) {
      NotificationService.start();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> login(String username, String password) async {
    _user = await AuthService.login(username, password);
    // 教师角色登录后启动后台通知轮询（管理员暂不推送）
    if (!(_user?.isAdmin ?? false)) {
      NotificationService.start();
    }
    notifyListeners();
  }

  Future<void> refreshCurrentUser() async {
    final user = await AuthService.tryAutoLogin();
    if (user != null) {
      _user = user;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    await AuthService.logout();
    await NotificationService.stop();
    _user = null;
    notifyListeners();
  }
}
