class StudentSelection {
  final int id;
  final int courseId;
  final String courseName;
  final int preference;
  final int round;
  final String status;
  final DateTime? selectedAt;
  final DateTime? confirmedAt;
  final bool canDrop;

  const StudentSelection({
    required this.id,
    required this.courseId,
    required this.courseName,
    required this.preference,
    required this.round,
    required this.status,
    this.selectedAt,
    this.confirmedAt,
    required this.canDrop,
  });

  factory StudentSelection.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic value) {
      if (value is int) return value;
      if (value is num) return value.toInt();
      return int.tryParse(value?.toString() ?? '') ?? 0;
    }

    return StudentSelection(
      id: toInt(json['id']),
      courseId: toInt(json['courseId']),
      courseName: json['courseName']?.toString() ?? '-',
      preference: toInt(json['preference']),
      round: toInt(json['round']),
      status: json['status']?.toString() ?? '-',
      selectedAt: json['selectedAt'] != null
          ? DateTime.tryParse(json['selectedAt'].toString())
          : null,
      confirmedAt: json['confirmedAt'] != null
          ? DateTime.tryParse(json['confirmedAt'].toString())
          : null,
      canDrop: json['canDrop'] == true,
    );
  }

  String get statusText {
    switch (status) {
      case 'CONFIRMED':
        return '已确认';
      case 'PENDING':
        return '待确认';
      case 'CANCELLED':
        return '已取消';
      default:
        return status;
    }
  }

  String get roundText {
    switch (round) {
      case 1:
        return '第一轮';
      case 2:
        return '第二轮';
      default:
        return '第$round轮';
    }
  }
}
