import '../models/school_class.dart';
import '../models/student.dart';
import '../models/physical_test.dart';
import '../models/term_grade.dart';
import 'api_service.dart';

class TeacherService {
  // 班级列表
  static Future<List<SchoolClass>> getClasses() async {
    final data = await ApiService.get('/teacher/classes') as List;
    return data.map((e) => SchoolClass.fromJson(e)).toList();
  }

  // 班级学生
  static Future<List<Student>> getStudents(int classId) async {
    final data = await ApiService.get('/teacher/classes/$classId/students') as List;
    return data.map((e) => Student.fromJson(e)).toList();
  }

  // 考勤查询
  static Future<Map<int, String>> getAttendance(int classId, String date) async {
    final data = await ApiService.get('/teacher/attendance?classId=$classId&date=$date');
    return (data as Map).map((k, v) => MapEntry(int.parse(k.toString()), v.toString()));
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
    return (data as Map).map((k, v) =>
        MapEntry(int.parse(k.toString()), PhysicalTest.fromJson(v)));
  }

  // 体测批量保存
  static Future<void> savePhysicalTests(List<Map<String, dynamic>> items) async {
    await ApiService.post('/teacher/physical-tests/save-batch', {'items': items} as Map<String, dynamic>);
  }

  // 成绩查询（返回 studentId → TermGrade）
  static Future<Map<int, TermGrade>> getTermGrades(
      int classId, String academicYear, String semester) async {
    final data = await ApiService.get(
        '/teacher/term-grades?classId=$classId&academicYear=${Uri.encodeComponent(academicYear)}&semester=${Uri.encodeComponent(semester)}');
    return (data as Map).map((k, v) =>
        MapEntry(int.parse(k.toString()), TermGrade.fromJson(v)));
  }

  // 成绩批量保存
  static Future<void> saveTermGrades(
      String academicYear, String semester, List<Map<String, dynamic>> items) async {
    await ApiService.post('/teacher/term-grades/save-batch', {
      'academicYear': academicYear,
      'semester': semester,
      'items': items,
    });
  }
}
