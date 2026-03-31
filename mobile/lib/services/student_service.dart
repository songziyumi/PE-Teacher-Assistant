import '../models/student_message.dart';
import '../models/student_request_course.dart';
import '../models/student_selection.dart';
import 'api_service.dart';

class StudentService {
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
