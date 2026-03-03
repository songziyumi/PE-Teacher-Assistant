import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import '../models/user.dart';
import 'api_service.dart';

class AuthService {
  static Future<UserModel> login(String username, String password) async {
    final res = await http.post(
      Uri.parse(ApiConfig.loginUrl),
      headers: {'Content-Type': 'application/json; charset=UTF-8'},
      body: jsonEncode({'username': username, 'password': password}),
    );
    final body = jsonDecode(utf8.decode(res.bodyBytes));
    if (body['code'] != 200) throw ApiException(body['code'], body['message'] ?? '登录失败');
    final user = UserModel.fromJson(body['data']);
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

  static Future<void> logout() => ApiService.clearToken();
}
