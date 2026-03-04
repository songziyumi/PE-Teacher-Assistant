class Student {
  final int id;
  final String name;
  final String? studentNo;
  final String? gender;
  final String? className;
  final String? gradeName;
  final int? classId;
  final String? electiveClass;

  const Student({
    required this.id,
    required this.name,
    this.studentNo,
    this.gender,
    this.className,
    this.gradeName,
    this.classId,
    this.electiveClass,
  });

  factory Student.fromJson(Map<String, dynamic> json) => Student(
        id: json['id'],
        name: json['name'] ?? '',
        studentNo: json['studentNo'],
        gender: json['gender'],
        className: json['schoolClass'] != null ? json['schoolClass']['name'] : null,
        gradeName: json['schoolClass']?['grade']?['name'],
        classId: json['schoolClass']?['id'],
        electiveClass: json['electiveClass'],
      );

  bool get isMale => gender == '男';
  bool get isFemale => gender == '女';

  String get displayClass {
    final parts = <String>[];
    if (gradeName != null) parts.add(gradeName!);
    if (className != null) parts.add(className!);
    return parts.join(' ');
  }
}
