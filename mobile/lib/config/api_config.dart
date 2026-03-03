// 服务器地址配置 — 修改 baseUrl 为你的服务器 IP
// 手机测试时用局域网 IP，如 http://192.168.1.100:8080
// 生产环境换成域名，如 https://your-domain.com

class ApiConfig {
  static String baseUrl = 'http://10.0.2.2:8080'; // Android 模拟器默认指向 PC localhost

  static String get apiBase => '$baseUrl/api';
  static String get loginUrl => '$apiBase/auth/login';
  static String get meUrl => '$apiBase/auth/me';
}
