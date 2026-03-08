import '../models/school_class.dart';
import '../models/student.dart';
import '../models/physical_test.dart';
import '../models/term_grade.dart';
import '../models/elective_class.dart';
import '../models/course_request.dart';
import '../models/teacher_message.dart';
import 'api_service.dart';

class TeacherService {
  // 班级列表
  static Future<List<SchoolClass>> getClasses() async {
    final data = await ApiService.get('/teacher/classes') as List;
    return data.map((e) => SchoolClass.fromJson(e)).toList();
  }

  // 年级列表
  static Future<List<Grade>> getGrades() async {
    final data = await ApiService.get('/teacher/grades') as List;
    return data.map((e) => Grade.fromJson(e)).toList();
  }

  // 全校行政班（用于学生班级修改）
  static Future<List<SchoolClass>> getSchoolClasses() async {
    final data = await ApiService.get('/teacher/school-classes') as List;
    return data.map((e) => SchoolClass.fromJson(e)).toList();
  }

  // 全校选修班
  static Future<List<ElectiveClass>> getElectiveClasses() async {
    final data = await ApiService.get('/teacher/elective-classes') as List;
    return data.map((e) => ElectiveClass.fromJson(e)).toList();
  }

  // 修改学生信息（基础信息 + 行政班 + 选修班）
  static Future<void> updateStudent(
    int studentId, {
    String? name,
    String? gender,
    String? studentNo,
    String? studentStatus,
    int? classId,
    String? electiveClass,
  }) async {
    await ApiService.put('/teacher/students/$studentId', {
      if (name != null) 'name': name,
      if (gender != null) 'gender': gender,
      if (studentNo != null) 'studentNo': studentNo,
      if (studentStatus != null) 'studentStatus': studentStatus,
      if (classId != null) 'classId': classId,
      'electiveClass': electiveClass,
    });
  }

  static Future<Map<String, dynamic>> checkStudentNoAvailability(
    String studentNo, {
    int? excludeId,
  }) async {
    final query = StringBuffer(
      '/teacher/students/check-student-no?studentNo=${Uri.encodeComponent(studentNo)}',
    );
    if (excludeId != null) {
      query.write('&excludeId=$excludeId');
    }
    final data = await ApiService.get(query.toString()) as Map;
    return {
      'available': data['available'] == true,
      'message': data['message']?.toString() ?? '',
    };
  }

  // 班级学生
  static Future<List<Student>> getStudents(int classId) async {
    final data =
        await ApiService.get('/teacher/classes/$classId/students') as List;
    return data.map((e) => Student.fromJson(e)).toList();
  }

  static Future<List<CourseRequest>> getCourseRequests(
      {String status = 'PENDING'}) async {
    final data = await ApiService.get(
            '/teacher/course-requests?status=${Uri.encodeComponent(status)}')
        as List;
    return data.map((e) => CourseRequest.fromJson(e)).toList();
  }

  static Future<Map<String, int>> getCourseRequestSummary() async {
    final data =
        await ApiService.get('/teacher/course-requests/summary') as Map;
    return {
      'pending': _toInt(data['pending']),
      'approved': _toInt(data['approved']),
      'rejected': _toInt(data['rejected']),
    };
  }

  static Future<CourseRequest> getCourseRequestDetail(int id) async {
    final data = await ApiService.get('/teacher/course-requests/$id') as Map;
    return CourseRequest.fromJson(Map<String, dynamic>.from(data));
  }

  static Future<void> approveCourseRequest(int id, {String? remark}) async {
    await ApiService.post('/teacher/course-requests/$id/approve', {
      if (remark != null) 'remark': remark,
    });
  }

  static Future<void> rejectCourseRequest(int id, {String? remark}) async {
    await ApiService.post('/teacher/course-requests/$id/reject', {
      if (remark != null) 'remark': remark,
    });
  }

  static Future<int> getUnreadMessageCount() async {
    final data = await ApiService.get('/teacher/messages/unread-count') as Map;
    return _toInt(data['unreadCount']);
  }

  static Future<List<TeacherMessage>> getTeacherMessages(
      {bool unreadOnly = false}) async {
    final data = await ApiService.get(
      '/teacher/messages?unreadOnly=${unreadOnly ? 'true' : 'false'}',
    ) as List;
    return data.map((e) => TeacherMessage.fromJson(e)).toList();
  }

  static Future<void> markTeacherMessageRead(int id) async {
    await ApiService.post('/teacher/messages/$id/read', {});
  }

  // 考勤查询
  static Future<Map<int, String>> getAttendance(
      int classId, String date) async {
    final data =
        await ApiService.get('/teacher/attendance?classId=$classId&date=$date');
    return (data as Map)
        .map((k, v) => MapEntry(int.parse(k.toString()), v.toString()));
  }

  // 考勤保存
  static Future<void> saveAttendance(
      int classId, String date, Map<int, String> statusMap) async {
    await ApiService.post('/teacher/attendance/save-batch', {
      'classId': classId,
      'date': date,
      'records': statusMap.entries
          .map((e) => {'studentId': e.key, 'status': e.value})
          .toList(),
    });
  }

  // 体测查询（返回 studentId → PhysicalTest）
  static Future<Map<int, PhysicalTest>> getPhysicalTests(
      int classId, String academicYear, String semester) async {
    final data = await ApiService.get(
        '/teacher/physical-tests?classId=$classId&academicYear=${Uri.encodeComponent(academicYear)}&semester=${Uri.encodeComponent(semester)}');
    return (data as Map).map(
        (k, v) => MapEntry(int.parse(k.toString()), PhysicalTest.fromJson(v)));
  }

  // 体测批量保存
  static Future<void> savePhysicalTests(
      List<Map<String, dynamic>> items) async {
    await ApiService.post('/teacher/physical-tests/save-batch',
        {'items': items} as Map<String, dynamic>);
  }

  // 成绩查询（返回 studentId → TermGrade）
  static Future<Map<int, TermGrade>> getTermGrades(
      int classId, String academicYear, String semester) async {
    final data = await ApiService.get(
        '/teacher/term-grades?classId=$classId&academicYear=${Uri.encodeComponent(academicYear)}&semester=${Uri.encodeComponent(semester)}');
    return (data as Map).map(
        (k, v) => MapEntry(int.parse(k.toString()), TermGrade.fromJson(v)));
  }

  // 成绩批量保存
  static Future<void> saveTermGrades(String academicYear, String semester,
      List<Map<String, dynamic>> items) async {
    await ApiService.post('/teacher/term-grades/save-batch', {
      'academicYear': academicYear,
      'semester': semester,
      'items': items,
    });
  }

  static int _toInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
