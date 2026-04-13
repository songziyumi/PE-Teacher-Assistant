import '../models/user.dart';
import 'api_service.dart';

class AuthService {
  static Future<UserModel> login(String username, String password) async {
    final data = await ApiService.post('/auth/login', {
      'username': username,
      'password': password,
    }) as Map;
    final user = UserModel.fromJson(Map<String, dynamic>.from(data));
    await ApiService.saveToken(user.token);
    return user;
  }

  static Future<UserModel?> tryAutoLogin() async {
    final token = await ApiService.getToken();
    if (token == null) return null;
    try {
      final data = await ApiService.get('/auth/me');
      return UserModel.fromJson(data as Map<String, dynamic>);
    } catch (_) {
      await ApiService.clearToken();
      return null;
    }
  }

  static Future<String> requestPasswordReset({
    required String account,
    required String email,
  }) async {
    final data = await ApiService.post('/auth/password-reset/request', {
      'account': account.trim(),
      'email': email.trim(),
    });
    if (data is String && data.trim().isNotEmpty) {
      return data;
    }
    return '如信息匹配，重置邮件已发送';
  }

  static Future<void> logout() => ApiService.clearToken();
}
