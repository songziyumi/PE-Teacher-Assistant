class UserModel {
  final String token;
  final String username;
  final String name;
  final String role;
  final int? schoolId;
  final String? schoolName;

  const UserModel({
    required this.token,
    required this.username,
    required this.name,
    required this.role,
    this.schoolId,
    this.schoolName,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) => UserModel(
        token: json['token'] ?? '',
        username: json['username'] ?? '',
        name: json['name'] ?? '',
        role: json['role'] ?? '',
        schoolId: json['schoolId'],
        schoolName: json['schoolName'],
      );

  bool get isAdmin => role == 'ADMIN';
  bool get isTeacher => role == 'TEACHER';
}
