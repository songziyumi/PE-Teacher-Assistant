import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'offline_queue_service.dart';

class NetworkService {
  static final _connectivity = Connectivity();
  static StreamSubscription<List<ConnectivityResult>>? _sub;

  static bool _online = true;
  static bool get isOnline => _online;

  static final List<void Function(bool)> _listeners = [];
  static void addListener(void Function(bool) cb) => _listeners.add(cb);
  static void removeListener(void Function(bool) cb) => _listeners.remove(cb);

  static Future<void> init() async {
    final results = await _connectivity.checkConnectivity();
    _online = _isConnected(results);
    _sub = _connectivity.onConnectivityChanged.listen(_onChanged);
  }

  static Future<void> _onChanged(List<ConnectivityResult> results) async {
    final nowOnline = _isConnected(results);
    if (nowOnline == _online) return;
    final wasOnline = _online;
    _online = nowOnline;
    for (final cb in List.of(_listeners)) {
      cb(nowOnline);
    }
    if (nowOnline && !wasOnline) {
      // Back online — flush the offline queue
      await OfflineQueueService.flush();
    }
  }

  static bool _isConnected(List<ConnectivityResult> results) =>
      results.any((r) => r != ConnectivityResult.none);

  static Future<void> dispose() async {
    await _sub?.cancel();
    _sub = null;
    _listeners.clear();
  }
}
