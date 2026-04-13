class StudentAccountRow {
  final int studentId;
  final String name;
  final String? studentNo;
  final String? gradeName;
  final String? className;
  final String loginId;
  final String loginAlias;
  final String status;
  final bool activated;
  final bool passwordChanged;
  final DateTime? lastLoginAt;
  final DateTime? lastPasswordResetAt;
  final bool enabled;

  const StudentAccountRow({
    required this.studentId,
    required this.name,
    this.studentNo,
    this.gradeName,
    this.className,
    required this.loginId,
    required this.loginAlias,
    required this.status,
    required this.activated,
    required this.passwordChanged,
    required this.lastLoginAt,
    required this.lastPasswordResetAt,
    required this.enabled,
  });

  factory StudentAccountRow.fromJson(Map<String, dynamic> json) {
    return StudentAccountRow(
      studentId: (json['studentId'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      studentNo: json['studentNo']?.toString(),
      gradeName: json['gradeName']?.toString(),
      className: json['className']?.toString(),
      loginId: json['loginId']?.toString() ?? '',
      loginAlias: json['loginAlias']?.toString() ?? '',
      status: json['status']?.toString() ?? '未生成',
      activated: json['activated'] == true,
      passwordChanged: json['passwordChanged'] == true,
      lastLoginAt: _parseDate(json['lastLoginAt']),
      lastPasswordResetAt: _parseDate(json['lastPasswordResetAt']),
      enabled: json['enabled'] == true,
    );
  }

  String get displayClass {
    final parts = <String>[];
    if (gradeName != null && gradeName!.isNotEmpty) parts.add(gradeName!);
    if (className != null && className!.isNotEmpty) parts.add(className!);
    return parts.join(' ');
  }

  bool get hasAccount => loginId.trim().isNotEmpty;
  bool get hasLoginAlias => loginAlias.trim().isNotEmpty;

  static DateTime? _parseDate(Object? raw) {
    if (raw == null) return null;
    if (raw is DateTime) return raw;
    final text = raw.toString().trim();
    if (text.isEmpty) return null;
    try {
      return DateTime.parse(text);
    } catch (_) {
      return null;
    }
  }
}
