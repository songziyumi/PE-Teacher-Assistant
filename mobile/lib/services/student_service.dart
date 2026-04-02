import '../models/student_message.dart';
import '../models/student_message_recipient.dart';
import '../models/student_request_course.dart';
import '../models/student_selection.dart';
import 'api_service.dart';

class StudentService {
  static Future<Map<String, dynamic>?> getCurrentEvent() async {
    final data = await ApiService.get('/student/events/current');
    if (data == null) return null;
    return Map<String, dynamic>.from(data as Map);
  }

  static Future<List<StudentRequestCourse>> getCurrentCourses() async {
    final data = await ApiService.get('/student/courses') as List;
    return data
        .map((e) => StudentRequestCourse.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  static Future<Map<String, dynamic>> getRequestableCourses() async {
    final data = await ApiService.get('/student/courses/requestable') as Map;
    final list = (data['courses'] as List? ?? const [])
        .map((e) => StudentRequestCourse.fromJson(Map<String, dynamic>.from(e)))
        .toList();

    return {
      'canRequest': data['canRequest'] == true,
      'reason': data['reason']?.toString() ?? '',
      'eventName': data['eventName']?.toString() ?? '',
      'courses': list,
    };
  }

  static Future<void> requestCourse(int courseId, {String? content}) async {
    await ApiService.post('/student/courses/$courseId/request', {
      'content': content?.trim() ?? '',
    });
  }

  static Future<void> submitPreference({
    required int courseId,
    required int preference,
  }) async {
    await ApiService.post(
      '/student/courses/$courseId/prefer?preference=$preference',
      {},
    );
  }

  static Future<void> saveRound1Draft() async {
    await ApiService.post('/student/courses/save-draft', {});
  }

  static Future<void> confirmRound1Selections() async {
    await ApiService.post('/student/courses/confirm', {});
  }

  static Future<void> selectCourse(int courseId) async {
    await ApiService.post('/student/courses/$courseId/select', {});
  }

  static Future<List<StudentSelection>> getMySelections() async {
    final data = await ApiService.get('/student/my-selections') as List;
    return data
        .map((e) => StudentSelection.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  static Future<void> dropSelection(int selectionId) async {
    await ApiService.delete('/student/selections/$selectionId');
  }

  static Future<int> getUnreadMessageCount() async {
    final data = await ApiService.get('/student/messages/unread-count') as Map;
    final value = data['unreadCount'];
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  static Future<List<StudentMessage>> getStudentMessages({
    bool unreadOnly = false,
  }) async {
    final data = await ApiService.get(
      '/student/messages?unreadOnly=${unreadOnly ? 'true' : 'false'}',
    ) as List;
    return data
        .map((e) => StudentMessage.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  static Future<List<StudentMessageRecipient>> getMessageRecipients() async {
    final data = await ApiService.get('/student/messages/recipients') as List;
    return data
        .map(
          (e) => StudentMessageRecipient.fromJson(Map<String, dynamic>.from(e)),
        )
        .toList();
  }

  static Future<void> sendStudentMessage({
    required int teacherId,
    required String subject,
    required String content,
  }) async {
    await ApiService.post('/student/messages/send', {
      'teacherId': teacherId,
      'subject': subject.trim(),
      'content': content.trim(),
    });
  }

  static Future<void> markStudentMessageRead(int id) async {
    await ApiService.post('/student/messages/$id/read', {});
  }

  static Future<void> changePassword({
    required String oldPassword,
    required String newPassword,
  }) async {
    await ApiService.post('/student/password/change', {
      'oldPassword': oldPassword,
      'newPassword': newPassword,
    });
  }
}
