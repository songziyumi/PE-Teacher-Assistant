import 'dart:convert';
import 'dart:typed_data';
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

  /// 发送 HTTP 请求，禁止自动跟随重定向（防止 302→HTML 被误当成 200 成功）
  static Future<http.Response> _send(
    String method,
    String path, {
    Object? body,
    bool json = true,
  }) async {
    final uri = Uri.parse('${ApiConfig.apiBase}$path');
    final request = http.Request(method, uri);
    request.headers.addAll(await _headers(json: json));
    request.followRedirects = false;
    if (body != null) request.body = jsonEncode(body);
    final streamed = await http.Client().send(request);
    return http.Response.fromStream(streamed);
  }

  static Future<dynamic> get(String path) async {
    final res = await _send('GET', path);
    return _handle(res);
  }

  static Future<dynamic> post(String path, Object body) async {
    final res = await _send('POST', path, body: body);
    return _handle(res);
  }

  static Future<dynamic> put(String path, Object body) async {
    final res = await _send('PUT', path, body: body);
    return _handle(res);
  }

  static Future<dynamic> delete(String path) async {
    final res = await _send('DELETE', path);
    return _handle(res);
  }

  /// 下载文件，返回原始字节（不走 JSON 解析）
  static Future<Uint8List> downloadFile(String path) async {
    final res = await _send('GET', path);
    if (res.statusCode == 200) return res.bodyBytes;
    // 尝试解析错误信息
    try {
      final body = utf8.decode(res.bodyBytes);
      if (!body.trimLeft().startsWith('<')) {
        final decoded = jsonDecode(body);
        if (decoded is Map && decoded['message'] != null) {
          throw ApiException(res.statusCode, decoded['message'].toString());
        }
      }
    } catch (e) {
      if (e is ApiException) rethrow;
    }
    throw ApiException(res.statusCode, '下载失败 (${res.statusCode})');
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
    // 302 重定向通常意味着 session 过期被踢到登录页，等同于 401
    if (res.statusCode >= 300 && res.statusCode < 400) {
      throw ApiException(401, '未授权，请重新登录');
    }
    final body = utf8.decode(res.bodyBytes);
    // Guard against HTML error pages (e.g. Spring Boot's 404/500 ErrorController)
    if (body.trimLeft().startsWith('<')) {
      if (res.statusCode == 401) throw ApiException(401, '未授权，请先登录');
      if (res.statusCode == 403) throw ApiException(403, '权限不足');
      if (res.statusCode == 404) throw ApiException(404, '接口不存在');
      throw ApiException(res.statusCode, '服务器错误 (${res.statusCode})');
    }
    final decoded = jsonDecode(body);

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
