class StudentRequestCourse {
  final int id;
  final String name;
  final String? description;
  final String? teacherName;
  final bool teacherAssigned;
  final int totalCapacity;
  final int currentCount;
  final int remaining;
  final String? capacityMode;
  final bool confirmed;
  final int myPreference;
  final bool hasPendingRequest;
  final String? requestStatus;

  const StudentRequestCourse({
    required this.id,
    required this.name,
    this.description,
    this.teacherName,
    required this.teacherAssigned,
    required this.totalCapacity,
    required this.currentCount,
    required this.remaining,
    this.capacityMode,
    required this.confirmed,
    required this.myPreference,
    required this.hasPendingRequest,
    this.requestStatus,
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
      teacherAssigned: json['teacherAssigned'] == true,
      totalCapacity: toInt(json['totalCapacity']),
      currentCount: toInt(json['currentCount']),
      remaining: toInt(json['remaining']),
      capacityMode: json['capacityMode']?.toString(),
      confirmed: json['confirmed'] == true,
      myPreference: toInt(json['myPreference']),
      hasPendingRequest: json['hasPendingRequest'] == true,
      requestStatus: json['requestStatus']?.toString(),
    );
  }
}
