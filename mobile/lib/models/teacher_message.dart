class TeacherMessage {
  final int id;
  final String? subject;
  final String? content;
  final String? type;
  final String? status;
  final bool isRead;
  final DateTime? sentAt;
  final String? senderType;
  final int? senderId;
  final String? senderName;
  final int? relatedCourseId;
  final String? relatedCourseName;
  final String? businessTargetType;
  final int? businessTargetId;

  const TeacherMessage({
    required this.id,
    this.subject,
    this.content,
    this.type,
    this.status,
    required this.isRead,
    this.sentAt,
    this.senderType,
    this.senderId,
    this.senderName,
    this.relatedCourseId,
    this.relatedCourseName,
    this.businessTargetType,
    this.businessTargetId,
  });

  TeacherMessage copyWith({
    int? id,
    String? subject,
    String? content,
    String? type,
    String? status,
    bool? isRead,
    DateTime? sentAt,
    String? senderType,
    int? senderId,
    String? senderName,
    int? relatedCourseId,
    String? relatedCourseName,
    String? businessTargetType,
    int? businessTargetId,
  }) {
    return TeacherMessage(
      id: id ?? this.id,
      subject: subject ?? this.subject,
      content: content ?? this.content,
      type: type ?? this.type,
      status: status ?? this.status,
      isRead: isRead ?? this.isRead,
      sentAt: sentAt ?? this.sentAt,
      senderType: senderType ?? this.senderType,
      senderId: senderId ?? this.senderId,
      senderName: senderName ?? this.senderName,
      relatedCourseId: relatedCourseId ?? this.relatedCourseId,
      relatedCourseName: relatedCourseName ?? this.relatedCourseName,
      businessTargetType: businessTargetType ?? this.businessTargetType,
      businessTargetId: businessTargetId ?? this.businessTargetId,
    );
  }

  factory TeacherMessage.fromJson(Map<String, dynamic> json) => TeacherMessage(
    id: _toInt(json['id']),
    subject: json['subject']?.toString(),
    content: json['content']?.toString(),
    type: json['type']?.toString(),
    status: json['status']?.toString(),
    isRead: json['isRead'] == true,
    sentAt: json['sentAt'] != null
        ? DateTime.tryParse(json['sentAt'].toString())
        : null,
    senderType: json['senderType']?.toString(),
    senderId: _toNullableInt(json['senderId']),
    senderName: json['senderName']?.toString(),
    relatedCourseId: _toNullableInt(json['relatedCourseId']),
    relatedCourseName: json['relatedCourseName']?.toString(),
    businessTargetType: json['businessTargetType']?.toString(),
    businessTargetId: _toNullableInt(json['businessTargetId']),
  );

  bool get canJumpToCourseRequest =>
      businessTargetType == 'COURSE_REQUEST' && businessTargetId != null;

  static int _toInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  static int? _toNullableInt(dynamic value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value.toString());
  }
}
