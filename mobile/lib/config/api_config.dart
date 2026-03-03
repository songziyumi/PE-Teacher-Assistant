// 服务器地址配置 — 修改 baseUrl 为你的服务器 IP
// 手机测试时用局域网 IP，如 http://192.168.1.100:8080
// 生产环境换成域名，如 https://your-domain.com

class ApiConfig {
  // 修改为你的公网服务器地址
  static String baseUrl = 'http://175.24.131.74:8080'; // 你的公网服务器

  static String get apiBase => '$baseUrl/api';
  static String get loginUrl => '$apiBase/auth/login';
  static String get meUrl => '$apiBase/auth/me';
}
