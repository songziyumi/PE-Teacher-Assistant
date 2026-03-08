import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';

class ApiService {
  static const _storage = FlutterSecureStorage();
  static const _tokenKey = 'jwt_token';

  static Future<String?> getToken() => _storage.read(key: _tokenKey);
  static Future<void> saveToken(String token) =>
      _storage.write(key: _tokenKey, value: token);
  static Future<void> clearToken() => _storage.delete(key: _tokenKey);

  static Future<Map<String, String>> _headers({bool json = true}) async {
    final token = await getToken();
    return {
      if (json) 'Content-Type': 'application/json; charset=UTF-8',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  static Future<dynamic> get(String path) async {
    final res = await http.get(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(json: true),
    );
    return _handle(res);
  }

  static Future<dynamic> post(String path, Map<String, dynamic> body) async {
    final res = await http.post(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(json: true),
      body: jsonEncode(body),
    );
    return _handle(res);
  }

  static Future<dynamic> put(String path, Map<String, dynamic> body) async {
    final res = await http.put(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(json: true),
      body: jsonEncode(body),
    );
    return _handle(res);
  }

  static Future<dynamic> delete(String path) async {
    final res = await http.delete(
      Uri.parse('${ApiConfig.apiBase}$path'),
      headers: await _headers(json: true),
    );
    return _handle(res);
  }

  static Future<dynamic> postMultipart(
    String path, {
    required String fileField,
    required String filePath,
    Map<String, String>? fields,
  }) async {
    final uri = Uri.parse('${ApiConfig.apiBase}$path');
    final req = http.MultipartRequest('POST', uri);
    req.headers.addAll(await _headers(json: false));
    if (fields != null) {
      req.fields.addAll(fields);
    }
    req.files.add(await http.MultipartFile.fromPath(fileField, filePath));
    final streamed = await req.send();
    final res = await http.Response.fromStream(streamed);
    return _handle(res);
  }

  static dynamic _handle(http.Response res) {
    final decoded = jsonDecode(utf8.decode(res.bodyBytes));

    if (decoded is Map<String, dynamic>) {
      final code = decoded['code'];
      final message = decoded['message']?.toString() ?? '请求失败';
      if (code is num && code.toInt() != 200) {
        throw ApiException(code.toInt(), message);
      }
      if (res.statusCode == 401) throw ApiException(401, message);
      if (res.statusCode == 403) throw ApiException(403, message);
      if (res.statusCode >= 400) throw ApiException(res.statusCode, message);
      return decoded['data'];
    }

    if (res.statusCode == 401) throw ApiException(401, '未授权，请先登录');
    if (res.statusCode == 403) throw ApiException(403, '权限不足');
    if (res.statusCode >= 400) throw ApiException(res.statusCode, '请求失败');
    return decoded;
  }
}

class ApiException implements Exception {
  final int code;
  final String message;

  ApiException(this.code, this.message);

  @override
  String toString() => message;
}
