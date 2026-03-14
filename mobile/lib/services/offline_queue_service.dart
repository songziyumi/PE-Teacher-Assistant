import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'teacher_service.dart';

/// Persistent queue for write operations that failed due to network errors.
/// Supports attendance, physical-test, and term-grade saves.
/// Each op type is deduplicated by its natural key so re-saves overwrite, not append.
class OfflineQueueService {
  static const _prefKey = 'offline_op_queue';

  static final List<void Function()> _listeners = [];
  static void addListener(void Function() cb) => _listeners.add(cb);
  static void removeListener(void Function() cb) => _listeners.remove(cb);
  static void _notify() {
    for (final cb in List.of(_listeners)) {
      cb();
    }
  }

  static Future<List<Map<String, dynamic>>> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getStringList(_prefKey) ?? [];
    return raw
        .map((s) => Map<String, dynamic>.from(jsonDecode(s) as Map))
        .toList();
  }

  static Future<void> _persist(List<Map<String, dynamic>> ops) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_prefKey, ops.map(jsonEncode).toList());
    _notify();
  }

  static Future<int> pendingCount() async => (await _load()).length;

  // ── Enqueue helpers ────────────────────────────────────────────────────────

  static Future<void> enqueueAttendance({
    required int classId,
    required String className,
    required String date,
    required Map<int, String> statusMap,
  }) async {
    final ops = await _load();
    ops.removeWhere((op) =>
        op['type'] == 'attendance' &&
        op['classId'] == classId &&
        op['date'] == date);
    ops.add({
      'type': 'attendance',
      'classId': classId,
      'className': className,
      'date': date,
      // Keys must be strings for JSON serialization
      'statusMap': statusMap.map((k, v) => MapEntry(k.toString(), v)),
      'queuedAt': DateTime.now().toIso8601String(),
    });
    await _persist(ops);
  }

  static Future<void> enqueuePhysicalTests({
    required int classId,
    required String className,
    required String academicYear,
    required String semester,
    required List<Map<String, dynamic>> items,
  }) async {
    final ops = await _load();
    ops.removeWhere((op) =>
        op['type'] == 'physical' &&
        op['classId'] == classId &&
        op['academicYear'] == academicYear &&
        op['semester'] == semester);
    ops.add({
      'type': 'physical',
      'classId': classId,
      'className': className,
      'academicYear': academicYear,
      'semester': semester,
      'items': items,
      'queuedAt': DateTime.now().toIso8601String(),
    });
    await _persist(ops);
  }

  static Future<void> enqueueTermGrades({
    required int classId,
    required String className,
    required String academicYear,
    required String semester,
    required List<Map<String, dynamic>> items,
  }) async {
    final ops = await _load();
    ops.removeWhere((op) =>
        op['type'] == 'grade' &&
        op['classId'] == classId &&
        op['academicYear'] == academicYear &&
        op['semester'] == semester);
    ops.add({
      'type': 'grade',
      'classId': classId,
      'className': className,
      'academicYear': academicYear,
      'semester': semester,
      'items': items,
      'queuedAt': DateTime.now().toIso8601String(),
    });
    await _persist(ops);
  }

  // ── Flush ──────────────────────────────────────────────────────────────────

  /// Attempt to replay all queued ops. Returns the number successfully flushed.
  /// Ops that still fail are kept for the next flush attempt.
  static Future<int> flush() async {
    final ops = await _load();
    if (ops.isEmpty) return 0;

    final remaining = <Map<String, dynamic>>[];
    int flushed = 0;

    for (final op in ops) {
      try {
        switch (op['type'] as String) {
          case 'attendance':
            final sm = (op['statusMap'] as Map).map(
              (k, v) => MapEntry(int.parse(k.toString()), v.toString()),
            );
            await TeacherService.saveAttendance(
                op['classId'] as int, op['date'] as String, sm);
          case 'physical':
            await TeacherService.savePhysicalTests(
              List<Map<String, dynamic>>.from(op['items'] as List),
            );
          case 'grade':
            await TeacherService.saveTermGrades(
              op['academicYear'] as String,
              op['semester'] as String,
              List<Map<String, dynamic>>.from(op['items'] as List),
            );
        }
        flushed++;
      } catch (_) {
        remaining.add(op);
      }
    }

    await _persist(remaining);
    return flushed;
  }

  static Future<void> clear() async {
    await _persist([]);
  }
}
