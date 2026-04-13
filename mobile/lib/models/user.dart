class UserModel {
  final String token;
  final String username;
  final String loginAlias;
  final String name;
  final String role;
  final int? schoolId;
  final String? schoolName;
  final bool mustChangePassword;

  const UserModel({
    required this.token,
    required this.username,
    this.loginAlias = '',
    required this.name,
    required this.role,
    this.schoolId,
    this.schoolName,
    this.mustChangePassword = false,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) => UserModel(
        token: json['token'] ?? '',
        username: json['username'] ?? '',
        loginAlias: json['loginAlias'] ?? '',
        name: json['name'] ?? '',
        role: json['role'] ?? '',
        schoolId: json['schoolId'],
        schoolName: json['schoolName'],
        mustChangePassword: json['mustChangePassword'] == true,
      );

  bool get isAdmin => role == 'ADMIN';
  bool get isTeacher => role == 'TEACHER';
  bool get isStudent => role == 'STUDENT';
}
