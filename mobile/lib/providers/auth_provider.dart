import 'package:flutter/material.dart';
import '../models/user.dart';
import '../services/auth_service.dart';

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
    _loading = false;
    notifyListeners();
  }

  Future<void> login(String username, String password) async {
    _user = await AuthService.login(username, password);
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
    _user = null;
    notifyListeners();
  }
}
