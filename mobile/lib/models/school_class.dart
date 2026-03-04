class SchoolClass {
  final int id;
  final String name;
  final String type;
  final String? gradeName;
  final int? gradeId;

  const SchoolClass({
    required this.id,
    required this.name,
    required this.type,
    this.gradeName,
    this.gradeId,
  });

  factory SchoolClass.fromJson(Map<String, dynamic> json) => SchoolClass(
        id: json['id'],
        name: json['name'] ?? '',
        type: json['type'] ?? '行政班',
        gradeName: json['gradeName'],
        gradeId: json['gradeId'],
      );

  String get displayName => gradeName != null ? '$gradeName $name' : name;
}

class Grade {
  final int id;
  final String name;

  const Grade({required this.id, required this.name});

  factory Grade.fromJson(Map<String, dynamic> json) =>
      Grade(id: json['id'], name: json['name'] ?? '');
}
