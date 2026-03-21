import 'dart:typed_data';
import '../models/school_class.dart';
import '../models/elective_class.dart';
import '../models/teacher_permission.dart';
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

  // 学生账号列表（分页）
  static Future<Map<String, dynamic>> getStudentAccounts({
    String keyword = '',
    int? classId,
    int? gradeId,
    String accountStatus = '',
    int page = 0,
    int size = 20,
  }) async {
    final q = StringBuffer('/admin/student-accounts?page=$page&size=$size');
    if (keyword.isNotEmpty) q.write('&keyword=${Uri.encodeComponent(keyword)}');
    if (classId != null) q.write('&classId=$classId');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    if (accountStatus.isNotEmpty) {
      q.write('&accountStatus=${Uri.encodeComponent(accountStatus)}');
    }
    return (await ApiService.get(q.toString())) as Map<String, dynamic>;
  }

  static Future<Map<String, dynamic>> batchGenerateStudentAccounts(
    Map<String, dynamic> params,
  ) async {
    final data =
        await ApiService.post('/admin/student-accounts/generate', params) as Map;
    return _normalizeBatchResult(data);
  }

  static Future<Map<String, dynamic>> batchResetStudentAccounts(
    Map<String, dynamic> params,
  ) async {
    final data = await ApiService.post(
        '/admin/student-accounts/reset-password', params) as Map;
    return _normalizeBatchResult(data);
  }

  static Future<Map<String, dynamic>> batchEnableStudentAccounts(
    Map<String, dynamic> params,
  ) async {
    final data =
        await ApiService.post('/admin/student-accounts/enable', params) as Map;
    return _normalizeBatchResult(data);
  }

  static Future<Map<String, dynamic>> batchDisableStudentAccounts(
    Map<String, dynamic> params,
  ) async {
    final data =
        await ApiService.post('/admin/student-accounts/disable', params) as Map;
    return _normalizeBatchResult(data);
  }

  static Future<Uint8List> exportStudentAccounts({
    List<int>? studentIds,
    int? gradeId,
    int? classId,
    String keyword = '',
    String accountStatus = '',
  }) async {
    final query = _buildStudentAccountExportQuery(
      studentIds: studentIds,
      gradeId: gradeId,
      classId: classId,
      keyword: keyword,
      accountStatus: accountStatus,
    );
    return ApiService.downloadFile('/admin/student-accounts/export?$query');
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

  static String _buildStudentAccountExportQuery({
    List<int>? studentIds,
    int? gradeId,
    int? classId,
    String keyword = '',
    String accountStatus = '',
  }) {
    final parts = <String>[];
    if (studentIds != null && studentIds.isNotEmpty) {
      for (final studentId in studentIds) {
        parts.add('studentIds=${Uri.encodeQueryComponent(studentId.toString())}');
      }
    }
    if (gradeId != null) {
      parts.add('gradeId=${Uri.encodeQueryComponent(gradeId.toString())}');
    }
    if (classId != null) {
      parts.add('classId=${Uri.encodeQueryComponent(classId.toString())}');
    }
    if (keyword.isNotEmpty) {
      parts.add('keyword=${Uri.encodeQueryComponent(keyword)}');
    }
    if (accountStatus.isNotEmpty) {
      parts.add('accountStatus=${Uri.encodeQueryComponent(accountStatus)}');
    }
    if (parts.isEmpty) return '_=1';
    return parts.join('&');
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

  // 导出审批记录（返回 xlsx 字节）
  static Future<Uint8List> exportCourseRequests() =>
      ApiService.downloadFile('/admin/course-requests/export');

  // 导出学生名单（返回 xlsx 字节）
  static Future<Uint8List> exportStudents({int? gradeId, int? classId}) async {
    final q = StringBuffer('/admin/students/export?_=1');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    if (classId != null) q.write('&classId=$classId');
    return ApiService.downloadFile(q.toString());
  }

  // 教师功能权限
  static Future<TeacherPermission> getTeacherPermissions() async {
    final data = await ApiService.get('/admin/teacher-permissions') as Map;
    return TeacherPermission.fromJson(Map<String, dynamic>.from(data));
  }

  static Future<TeacherPermission> updateTeacherPermissions(
      TeacherPermission perm) async {
    final data = await ApiService.put(
        '/admin/teacher-permissions', perm.toJson()) as Map;
    return TeacherPermission.fromJson(Map<String, dynamic>.from(data));
  }

  // 导出考勤记录（返回 xlsx 字节）
  static Future<Uint8List> exportAttendance({
    required String startDate,
    String? endDate,
    int? gradeId,
    int? classId,
    String? status,
  }) async {
    final q = StringBuffer(
      '/admin/attendance/export?startDate=${Uri.encodeComponent(startDate)}',
    );
    if (endDate != null) q.write('&endDate=${Uri.encodeComponent(endDate)}');
    if (gradeId != null) q.write('&gradeId=$gradeId');
    if (classId != null) q.write('&classId=$classId');
    if (status != null) q.write('&status=${Uri.encodeComponent(status)}');
    return ApiService.downloadFile(q.toString());
  }

  static Future<Map<String, dynamic>> getOperationTimeline({
    int page = 0,
    int size = 20,
    int? teacherId,
  }) async {
    final q = StringBuffer('/admin/operation-timeline?page=$page&size=$size');
    if (teacherId != null) q.write('&teacherId=$teacherId');
    final data = await ApiService.get(q.toString());
    return data as Map<String, dynamic>;
  }
}
