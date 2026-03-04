import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../config/api_config.dart';

class ApiService {
  static final _storage = const FlutterSecureStorage();
  static const _tokenKey = 'jwt_token';

  static Future<String?> getToken() => _storage.read(key: _tokenKey);
  static Future<void> saveToken(String token) => _storage.write(key: _tokenKey, value: token);
  static Future<void> clearToken() => _storage.delete(key: _tokenKey);

  static Future<Map<String, String>> _headers() async {
    final token = await getToken();
    return {
      'Content-Type': 'application/json; charset=UTF-8',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  static Future<dynamic> get(String path) async {
    final res = await http.get(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(),
    );
    return _handle(res);
  }

  static Future<dynamic> post(String path, Map<String, dynamic> body) async {
    final res = await http.post(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(),
      body: jsonEncode(body),
    );
    return _handle(res);
  }

  static Future<dynamic> put(String path, Map<String, dynamic> body) async {
    final res = await http.put(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(),
      body: jsonEncode(body),
    );
    return _handle(res);
  }

  static Future<dynamic> delete(String path) async {
    final res = await http.delete(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(),
    );
    return _handle(res);
  }

  static dynamic _handle(http.Response res) {
    final decoded = jsonDecode(utf8.decode(res.bodyBytes));
    if (res.statusCode == 401) throw ApiException(401, decoded['message'] ?? '未授权');
    if (res.statusCode == 403) throw ApiException(403, decoded['message'] ?? '权限不足');
    if (res.statusCode >= 400) throw ApiException(res.statusCode, decoded['message'] ?? '请求失败');
    return decoded['data'];
  }
}

class ApiException implements Exception {
  final int code;
  final String message;
  ApiException(this.code, this.message);
  @override
  String toString() => message;
}
