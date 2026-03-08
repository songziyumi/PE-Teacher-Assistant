class CourseRequest {
  final int id;
  final String? subject;
  final String? content;
  final String status;
  final String? type;
  final int? senderId;
  final String? senderName;
  final int? relatedCourseId;
  final String? relatedCourseName;
  final bool isRead;
  final DateTime? sentAt;
  final int? handledById;
  final String? handledByName;
  final DateTime? handledAt;
  final String? handleRemark;
  final List<CourseRequestAuditLog> auditLogs;

  const CourseRequest({
    required this.id,
    this.subject,
    this.content,
    required this.status,
    this.type,
    this.senderId,
    this.senderName,
    this.relatedCourseId,
    this.relatedCourseName,
    required this.isRead,
    this.sentAt,
    this.handledById,
    this.handledByName,
    this.handledAt,
    this.handleRemark,
    this.auditLogs = const [],
  });

  factory CourseRequest.fromJson(Map<String, dynamic> json) => CourseRequest(
        id: _toInt(json['id']),
        subject: json['subject']?.toString(),
        content: json['content']?.toString(),
        status: (json['status'] ?? 'PENDING').toString(),
        type: json['type']?.toString(),
        senderId: _toNullableInt(json['senderId']),
        senderName: json['senderName']?.toString(),
        relatedCourseId: _toNullableInt(json['relatedCourseId']),
        relatedCourseName: json['relatedCourseName']?.toString(),
        isRead: json['isRead'] == true,
        sentAt: json['sentAt'] != null
            ? DateTime.tryParse(json['sentAt'].toString())
            : null,
        handledById: _toNullableInt(json['handledById']),
        handledByName: json['handledByName']?.toString(),
        handledAt: json['handledAt'] != null
            ? DateTime.tryParse(json['handledAt'].toString())
            : null,
        handleRemark: json['handleRemark']?.toString(),
        auditLogs: (json['auditLogs'] is List)
            ? (json['auditLogs'] as List)
                .map((e) => CourseRequestAuditLog.fromJson(
                    Map<String, dynamic>.from(e as Map)))
                .toList()
            : const [],
      );

  String get statusLabel {
    switch (status) {
      case 'APPROVED':
        return '已通过';
      case 'REJECTED':
        return '已拒绝';
      default:
        return '待审批';
    }
  }

  bool get isPending => status == 'PENDING';

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

class CourseRequestAuditLog {
  final int id;
  final int? requestMessageId;
  final String? action;
  final String? beforeStatus;
  final String? afterStatus;
  final int? operatorTeacherId;
  final String? operatorTeacherName;
  final int? senderId;
  final String? senderName;
  final int? relatedCourseId;
  final String? relatedCourseName;
  final String? remark;
  final DateTime? handledAt;

  const CourseRequestAuditLog({
    required this.id,
    this.requestMessageId,
    this.action,
    this.beforeStatus,
    this.afterStatus,
    this.operatorTeacherId,
    this.operatorTeacherName,
    this.senderId,
    this.senderName,
    this.relatedCourseId,
    this.relatedCourseName,
    this.remark,
    this.handledAt,
  });

  factory CourseRequestAuditLog.fromJson(Map<String, dynamic> json) =>
      CourseRequestAuditLog(
        id: CourseRequest._toInt(json['id']),
        requestMessageId: CourseRequest._toNullableInt(json['requestMessageId']),
        action: json['action']?.toString(),
        beforeStatus: json['beforeStatus']?.toString(),
        afterStatus: json['afterStatus']?.toString(),
        operatorTeacherId:
            CourseRequest._toNullableInt(json['operatorTeacherId']),
        operatorTeacherName: json['operatorTeacherName']?.toString(),
        senderId: CourseRequest._toNullableInt(json['senderId']),
        senderName: json['senderName']?.toString(),
        relatedCourseId: CourseRequest._toNullableInt(json['relatedCourseId']),
        relatedCourseName: json['relatedCourseName']?.toString(),
        remark: json['remark']?.toString(),
        handledAt: json['handledAt'] != null
            ? DateTime.tryParse(json['handledAt'].toString())
            : null,
      );

  String get actionLabel {
    if (afterStatus == 'APPROVED') return '同意';
    if (afterStatus == 'REJECTED') return '拒绝';
    if (action == 'APPROVE') return '同意';
    if (action == 'REJECT') return '拒绝';
    return action ?? '-';
  }
}
