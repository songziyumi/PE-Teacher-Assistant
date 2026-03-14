import 'dart:typed_data';
import '../models/school_class.dart';
import '../models/elective_class.dart';
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

  // 选修班列表
  static Future<List<ElectiveClass>> getElectiveClasses() async {
    final data = await ApiService.get('/admin/elective-classes') as List;
    return data.map((e) => ElectiveClass.fromJson(e)).toList();
  }

  // 保存学生（新建/编辑）
  static Future<void> saveStudent({
    int? id,
    required String name,
    required String gender,
    required String studentNo,
    String? idCard,
    String? electiveClass,
    required int classId,
    String? studentStatus,
  }) async {
    await ApiService.post('/admin/students/save', {
      if (id != null) 'id': id,
      'name': name,
      'gender': gender,
      'studentNo': studentNo,
      if (idCard != null) 'idCard': idCard,
      if (electiveClass != null && electiveClass.isNotEmpty) 'electiveClass': electiveClass,
      'classId': classId,
      if (studentStatus != null && studentStatus.isNotEmpty) 'studentStatus': studentStatus,
    });
  }

  static Future<bool> checkStudentNo(String studentNo, {int? excludeId}) async {
    final q = StringBuffer('/admin/students/check-student-no?studentNo=${Uri.encodeComponent(studentNo)}');
    if (excludeId != null) q.write('&excludeId=$excludeId');
    final data = await ApiService.get(q.toString()) as Map;
    return data['available'] == true;
  }

  static Future<Map<String, dynamic>> batchUpdateStudentStatus(
    List<int> studentIds,
    String studentStatus,
  ) async {
    final data = await ApiService.post('/admin/students/batch-update-status', {
      'studentIds': studentIds,
      'studentStatus': studentStatus,
    }) as Map;
    return _normalizeBatchResult(data);
  }

  static Future<Map<String, dynamic>> batchUpdateStudentElectiveClass(
    List<int> studentIds, {
    String? electiveClass,
  }) async {
    final data = await ApiService.post('/admin/students/batch-update-elective-class', {
      'studentIds': studentIds,
      'electiveClass': electiveClass,
    }) as Map;
    return _normalizeBatchResult(data);
  }

  static Future<Map<String, dynamic>> batchDeleteStudents(
    List<int> studentIds,
  ) async {
    final data = await ApiService.post('/admin/students/batch-delete', {
      'studentIds': studentIds,
    }) as Map;
    return _normalizeBatchResult(data);
  }

  static Map<String, dynamic> _normalizeBatchResult(Map data) {
    final failedItems = ((data['failedItems'] as List?) ?? const [])
        .map((item) {
          final entry = Map<String, dynamic>.from((item as Map?) ?? const {});
          return {'id': entry['id'], 'reason': entry['reason']?.toString() ?? ''};
        })
        .toList(growable: false);
    return {
      'totalCount': (data['totalCount'] as num?)?.toInt() ?? 0,
      'successCount': (data['successCount'] as num?)?.toInt() ?? 0,
      'failedCount': (data['failedCount'] as num?)?.toInt() ?? 0,
      'failedItems': failedItems,
    };
  }

  static Future<void> deleteStudent(int id) => ApiService.delete('/admin/students/$id');

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

  // 导出考勤记录（返回 xlsx 字节）
  static Future<Uint8List> exportAttendance({
    required String startDate,
    String? endDate,
    int? gradeId,
    int? classId,
  }) async {
    final q = StringBuffer(
      '/admin/attendance/export?startDate=${Uri.encodeComponent(startDate)}',
    );
    if (endDate != null) q.write('&endDate=${Uri.encodeComponent(endDate)}');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    if (classId != null) q.write('&classId=$classId');
    return ApiService.downloadFile(q.toString());
  }
}
