import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class TeacherBottomNav extends StatelessWidget {
  final int currentIndex;

  const TeacherBottomNav({super.key, required this.currentIndex});

  @override
  Widget build(BuildContext context) {
    return NavigationBar(
      selectedIndex: currentIndex,
      destinations: const [
        NavigationDestination(icon: Icon(Icons.home_outlined), label: '首页'),
        NavigationDestination(icon: Icon(Icons.approval_outlined), label: '审批'),
        NavigationDestination(icon: Icon(Icons.mail_outline), label: '消息'),
        NavigationDestination(icon: Icon(Icons.person_outline), label: '我的'),
      ],
      onDestinationSelected: (index) {
        switch (index) {
          case 0:
            context.go('/teacher');
            break;
          case 1:
            context.go('/teacher/course-requests');
            break;
          case 2:
            context.go('/teacher/messages');
            break;
          case 3:
            context.go('/teacher/profile');
            break;
          default:
            context.go('/teacher');
        }
      },
    );
  }
}
