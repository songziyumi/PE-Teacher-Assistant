import 'dart:convert';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:workmanager/workmanager.dart';

import '../config/api_config.dart';

const _taskUniqueName = 'pe_teacher_notification_check';
const _prefKeyLastUnread = 'notification_last_unread_count';

/// Workmanager 后台任务入口（必须是顶层函数，且标注 vm:entry-point）
@pragma('vm:entry-point')
void notificationCallbackDispatcher() {
  Workmanager().executeTask((task, _) async {
    if (task == _taskUniqueName) {
      await NotificationService._backgroundCheck();
    }
    return Future.value(true);
  });
}

class NotificationService {
  static final _plugin = FlutterLocalNotificationsPlugin();

  static const _channelId = 'pe_teacher_messages';
  static const _channelName = '消息通知';
  static const _channelDesc = '新选课申请及站内消息提醒';

  /// 应用启动时调用一次（main.dart）
  static Future<void> init() async {
    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosInit = DarwinInitializationSettings(
      requestAlertPermission: false, // 登录后再请求，避免冷启动弹权限
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    await _plugin.initialize(
      const InitializationSettings(android: androidInit, iOS: iosInit),
    );

    // 初始化 Workmanager（不在这里注册任务，登录后再注册）
    await Workmanager().initialize(
      notificationCallbackDispatcher,
    );
  }

  /// 登录成功后调用 —— 请求通知权限并启动后台轮询
  static Future<void> start() async {
    // 请求 Android 13+ 通知权限
    await _plugin
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();

    // 请求 iOS 权限
    await _plugin
        .resolvePlatformSpecificImplementation<
            IOSFlutterLocalNotificationsPlugin>()
        ?.requestPermissions(alert: true, badge: true, sound: true);

    // 注册后台周期任务（每 15 分钟轮询一次，Android 最小间隔限制）
    await Workmanager().registerPeriodicTask(
      _taskUniqueName,
      _taskUniqueName,
      frequency: const Duration(minutes: 15),
      existingWorkPolicy: ExistingPeriodicWorkPolicy.replace,
      constraints: Constraints(networkType: NetworkType.connected),
    );
  }

  /// 退出登录后调用 —— 取消后台轮询并清除计数基线
  static Future<void> stop() async {
    await Workmanager().cancelByUniqueName(_taskUniqueName);
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefKeyLastUnread);
  }

  /// 消息列表加载完成后调用，同步未读基线（避免重复提醒已看到的消息）
  static Future<void> syncUnreadCount(int count) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_prefKeyLastUnread, count);
  }

  /// 立即检查一次（App 切回前台时触发，静默通知）
  static Future<void> checkNow() async {
    await _backgroundCheck();
  }

  // ────────────────────────────────────────────
  // 内部方法（也供 Workmanager 后台 isolate 调用）
  // ────────────────────────────────────────────

  static Future<void> _backgroundCheck() async {
    try {
      const storage = FlutterSecureStorage();
      final token = await storage.read(key: 'jwt_token');
      if (token == null) return; // 未登录则跳过

      final res = await http
          .get(
            Uri.parse('${ApiConfig.apiBase}/teacher/messages/unread-count'),
            headers: {'Authorization': 'Bearer $token'},
          )
          .timeout(const Duration(seconds: 15));

      if (res.statusCode != 200) return;

      final body = jsonDecode(utf8.decode(res.bodyBytes));
      final newCount = (body['data'] as num?)?.toInt() ?? 0;

      final prefs = await SharedPreferences.getInstance();
      final lastCount = prefs.getInt(_prefKeyLastUnread) ?? 0;

      if (newCount > lastCount) {
        await _showNotification(newCount - lastCount);
      }
      // 无论是否通知都更新基线（包含 newCount < lastCount 的场景）
      await prefs.setInt(_prefKeyLastUnread, newCount);
    } catch (_) {
      // 后台任务不能抛异常，静默忽略网络/解析错误
    }
  }

  static Future<void> _showNotification(int delta) async {
    const androidDetails = AndroidNotificationDetails(
      _channelId,
      _channelName,
      channelDescription: _channelDesc,
      importance: Importance.high,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
    );
    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );
    await _plugin.show(
      0, // notificationId —— 固定为 0，新通知覆盖旧通知
      '体育教师助手',
      '您有 $delta 条新消息',
      const NotificationDetails(android: androidDetails, iOS: iosDetails),
    );
  }
}
