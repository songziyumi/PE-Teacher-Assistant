class ApiConfig {
  // Use --dart-define=API_BASE_URL=http://<host>:<port> for real-device debugging.
  static String baseUrl = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://175.24.131.74:8080',
  );

  static String get apiBase => '$baseUrl/api';
  static String get loginUrl => '$apiBase/auth/login';
  static String get meUrl => '$apiBase/auth/me';

  static String normalizeBaseUrl(String raw) {
    var normalized = raw.trim();
    if (normalized.isEmpty) {
      return normalized;
    }
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1);
    }
    if (normalized.endsWith('/login')) {
      normalized = normalized.substring(0, normalized.length - '/login'.length);
    }
    if (normalized.endsWith('/api')) {
      normalized = normalized.substring(0, normalized.length - '/api'.length);
    }
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1);
    }
    return normalized;
  }
}
