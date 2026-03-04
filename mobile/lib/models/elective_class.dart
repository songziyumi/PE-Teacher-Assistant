class ElectiveClass {
  final int id;
  final String name;
  final String? gradeName;
  final int? gradeId;
  final String storedName; // 存入 student.electiveClass 的值，如 "高二/篮球"

  const ElectiveClass({
    required this.id,
    required this.name,
    this.gradeName,
    this.gradeId,
    required this.storedName,
  });

  factory ElectiveClass.fromJson(Map<String, dynamic> json) => ElectiveClass(
        id: json['id'],
        name: json['name'] ?? '',
        gradeName: json['gradeName'],
        gradeId: json['gradeId'],
        storedName: json['storedName'] ?? json['name'] ?? '',
      );

  String get displayName => gradeName != null ? '$gradeName / $name' : name;
}
