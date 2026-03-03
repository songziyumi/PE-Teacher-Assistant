import '../models/school_class.dart';
import '../models/student.dart';
import '../models/physical_test.dart';
import '../models/term_grade.dart';
import 'api_service.dart';

class AdminService {
  // 仪表盘统计
  static Future<Map<String, dynamic>> getDashboard() async {
    final data = await ApiService.get('/admin/dashboard');
    return data as Map<String, dynamic>;
  }

  // 年级列表
  static Future<List<Grade>> getGrades() async {
    final data = await ApiService.get('/admin/grades') as List;
    return data.map((e) => Grade.fromJson(e)).toList();
  }

  // 班级列表
  static Future<List<SchoolClass>> getClasses({int? gradeId}) async {
    final q = gradeId != null ? '?gradeId=$gradeId' : '';
    final data = await ApiService.get('/admin/classes$q') as List;
    return data.map((e) => SchoolClass.fromJson(e)).toList();
  }

  // 学年列表
  static Future<List<String>> getAcademicYears() async {
    final data = await ApiService.get('/admin/academic-years') as List;
    return data.cast<String>();
  }

  // 学生列表（分页）
  static Future<Map<String, dynamic>> getStudents({
    String keyword = '',
    int? classId,
    int? gradeId,
    int page = 0,
  }) async {
    final q = StringBuffer('/admin/students?page=$page');
    if (keyword.isNotEmpty) q.write('&keyword=${Uri.encodeComponent(keyword)}');
    if (classId != null) q.write('&classId=$classId');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    return (await ApiService.get(q.toString())) as Map<String, dynamic>;
  }

  // 体测列表（分页）
  static Future<Map<String, dynamic>> getPhysicalTests({
    int? classId, int? gradeId,
    String academicYear = '', String semester = '', String keyword = '',
    int page = 0,
  }) async {
    final q = StringBuffer('/admin/physical-tests?page=$page');
    if (classId != null) q.write('&classId=$classId');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    if (academicYear.isNotEmpty) q.write('&academicYear=${Uri.encodeComponent(academicYear)}');
    if (semester.isNotEmpty) q.write('&semester=${Uri.encodeComponent(semester)}');
    if (keyword.isNotEmpty) q.write('&keyword=${Uri.encodeComponent(keyword)}');
    return (await ApiService.get(q.toString())) as Map<String, dynamic>;
  }

  static Future<void> deletePhysicalTest(int id) =>
      ApiService.delete('/admin/physical-tests/$id');

  // 成绩列表（分页）
  static Future<Map<String, dynamic>> getTermGrades({
    int? classId, int? gradeId,
    String academicYear = '', String semester = '', String keyword = '',
    int page = 0,
  }) async {
    final q = StringBuffer('/admin/term-grades?page=$page');
    if (classId != null) q.write('&classId=$classId');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    if (academicYear.isNotEmpty) q.write('&academicYear=${Uri.encodeComponent(academicYear)}');
    if (semester.isNotEmpty) q.write('&semester=${Uri.encodeComponent(semester)}');
    if (keyword.isNotEmpty) q.write('&keyword=${Uri.encodeComponent(keyword)}');
    return (await ApiService.get(q.toString())) as Map<String, dynamic>;
  }

  static Future<void> deleteTermGrade(int id) =>
      ApiService.delete('/admin/term-grades/$id');
}
