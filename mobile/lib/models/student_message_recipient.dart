class StudentMessageRecipient {
  final int id;
  final String name;
  final String displayName;

  const StudentMessageRecipient({
    required this.id,
    required this.name,
    required this.displayName,
  });

  factory StudentMessageRecipient.fromJson(Map<String, dynamic> json) {
    return StudentMessageRecipient(
      id: _toInt(json['id']),
      name: json['name']?.toString() ?? '',
      displayName: json['displayName']?.toString() ?? '',
    );
  }

  static int _toInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
