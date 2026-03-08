class StudentRequestCourse {
  final int id;
  final String name;
  final String? description;
  final String? teacherName;
  final int totalCapacity;
  final int currentCount;
  final int remaining;
  final String? capacityMode;

  const StudentRequestCourse({
    required this.id,
    required this.name,
    this.description,
    this.teacherName,
    required this.totalCapacity,
    required this.currentCount,
    required this.remaining,
    this.capacityMode,
  });

  factory StudentRequestCourse.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic value) {
      if (value is int) return value;
      if (value is num) return value.toInt();
      return int.tryParse(value?.toString() ?? '') ?? 0;
    }

    return StudentRequestCourse(
      id: toInt(json['id']),
      name: json['name']?.toString() ?? '',
      description: json['description']?.toString(),
      teacherName: json['teacherName']?.toString(),
      totalCapacity: toInt(json['totalCapacity']),
      currentCount: toInt(json['currentCount']),
      remaining: toInt(json['remaining']),
      capacityMode: json['capacityMode']?.toString(),
    );
  }
}
