import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class StudentBottomNav extends StatelessWidget {
  final int currentIndex;

  const StudentBottomNav({super.key, required this.currentIndex});

  @override
  Widget build(BuildContext context) {
    return NavigationBar(
      selectedIndex: currentIndex,
      destinations: const [
        NavigationDestination(icon: Icon(Icons.home_outlined), label: '首页'),
        NavigationDestination(icon: Icon(Icons.book_outlined), label: '我的选课'),
        NavigationDestination(icon: Icon(Icons.mail_outline), label: '消息'),
        NavigationDestination(icon: Icon(Icons.lock_outline), label: '密码'),
      ],
      onDestinationSelected: (index) {
        switch (index) {
          case 0:
            context.go('/student');
            break;
          case 1:
            context.go('/student/my-courses');
            break;
          case 2:
            context.go('/student/messages');
            break;
          case 3:
            context.go('/student/password');
            break;
          default:
            context.go('/student');
        }
      },
    );
  }
}
