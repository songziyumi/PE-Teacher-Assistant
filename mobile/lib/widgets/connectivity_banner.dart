import 'package:flutter/material.dart';
import '../services/network_service.dart';

/// An orange banner shown at the top of write screens when the device is offline.
/// Automatically hides when connectivity is restored.
class ConnectivityBanner extends StatefulWidget {
  const ConnectivityBanner({super.key});

  @override
  State<ConnectivityBanner> createState() => _ConnectivityBannerState();
}

class _ConnectivityBannerState extends State<ConnectivityBanner> {
  bool _online = NetworkService.isOnline;

  void _onChanged(bool online) {
    if (mounted) setState(() => _online = online);
  }

  @override
  void initState() {
    super.initState();
    NetworkService.addListener(_onChanged);
  }

  @override
  void dispose() {
    NetworkService.removeListener(_onChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_online) return const SizedBox.shrink();
    return Container(
      width: double.infinity,
      color: Colors.orange.shade700,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: const Row(
        children: [
          Icon(Icons.wifi_off, size: 16, color: Colors.white),
          SizedBox(width: 8),
          Expanded(
            child: Text(
              '当前离线，保存操作将暂存本地，联网后自动同步',
              style: TextStyle(color: Colors.white, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}
