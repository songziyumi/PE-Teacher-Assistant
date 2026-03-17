import '../models/teacher_permission.dart';

/// 静态缓存教师权限，由 TeacherHome 启动时加载一次，供各教师端页面使用。
class PermissionCache {
  PermissionCache._();
  static TeacherPermission current = TeacherPermission.defaultAll;
}
