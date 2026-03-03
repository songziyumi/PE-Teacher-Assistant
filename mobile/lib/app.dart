import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'providers/auth_provider.dart';
import 'screens/auth/login_screen.dart';
import 'screens/teacher/teacher_home.dart';
import 'screens/teacher/attendance_screen.dart';
import 'screens/teacher/physical_entry.dart';
import 'screens/teacher/grade_entry.dart';
import 'screens/admin/admin_home.dart';
import 'screens/admin/student_list.dart';
import 'screens/admin/physical_list.dart';
import 'screens/admin/grade_list.dart';

GoRouter buildRouter(AuthProvider auth) => GoRouter(
      initialLocation: '/login',
      refreshListenable: auth,
      redirect: (context, state) {
        if (auth.loading) return null;
        final loggedIn = auth.isLoggedIn;
        final loc = state.matchedLocation;
        final onLogin = loc == '/login';
        if (!loggedIn && !onLogin) return '/login';
        if (loggedIn && onLogin) return auth.isAdmin ? '/admin' : '/teacher';
        if (loggedIn && loc == '/') return auth.isAdmin ? '/admin' : '/teacher';
        return null;
      },
      routes: [
        GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
        // 教师路由
        GoRoute(path: '/teacher', builder: (_, __) => const TeacherHome()),
        GoRoute(
          path: '/teacher/attendance/:classId',
          builder: (_, state) => AttendanceScreen(
            classId: int.parse(state.pathParameters['classId']!),
            className: state.uri.queryParameters['name'] ?? '',
          ),
        ),
        GoRoute(
          path: '/teacher/physical/:classId',
          builder: (_, state) => PhysicalEntryScreen(
            classId: int.parse(state.pathParameters['classId']!),
            className: state.uri.queryParameters['name'] ?? '',
          ),
        ),
        GoRoute(
          path: '/teacher/grade/:classId',
          builder: (_, state) => GradeEntryScreen(
            classId: int.parse(state.pathParameters['classId']!),
            className: state.uri.queryParameters['name'] ?? '',
          ),
        ),
        // 管理员路由
        GoRoute(path: '/admin', builder: (_, __) => const AdminHome()),
        GoRoute(path: '/admin/students', builder: (_, __) => const StudentListScreen()),
        GoRoute(path: '/admin/physical-tests', builder: (_, __) => const PhysicalListScreen()),
        GoRoute(path: '/admin/term-grades', builder: (_, __) => const GradeListScreen()),
      ],
    );
