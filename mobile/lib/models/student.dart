class Student {
  final int id;
  final String name;
  final String? studentNo;
  final String? gender;
  final String? className;
  final String? gradeName;

  const Student({
    required this.id,
    required this.name,
    this.studentNo,
    this.gender,
    this.className,
    this.gradeName,
  });

  factory Student.fromJson(Map<String, dynamic> json) => Student(
        id: json['id'],
        name: json['name'] ?? '',
        studentNo: json['studentNo'],
        gender: json['gender'],
        className: json['schoolClass'] != null ? json['schoolClass']['name'] : null,
        gradeName: json['schoolClass']?['grade']?['name'],
      );

  bool get isMale => gender == '男';
  bool get isFemale => gender == '女';
}
