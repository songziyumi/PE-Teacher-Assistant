import 'package:go_router/go_router.dart';
import 'providers/auth_provider.dart';
import 'screens/auth/login_screen.dart';
import 'screens/teacher/teacher_home.dart';
import 'screens/teacher/attendance_screen.dart';
import 'screens/teacher/teacher_student_list.dart';
import 'screens/teacher/physical_entry.dart';
import 'screens/teacher/grade_entry.dart';
import 'screens/teacher/course_request_center.dart';
import 'screens/teacher/course_request_detail.dart';
import 'screens/teacher/teacher_message_center.dart';
import 'screens/teacher/teacher_profile_screen.dart';
import 'screens/teacher/attendance_export_screen.dart';
import 'screens/teacher/data_export_screen.dart';
import 'screens/admin/admin_home.dart';
import 'screens/admin/student_list.dart';
import 'screens/admin/physical_list.dart';
import 'screens/admin/grade_list.dart';
import 'screens/admin/attendance_export_screen.dart';
import 'screens/admin/data_export_screen.dart';
import 'screens/admin/teacher_permission_screen.dart';
import 'screens/student/student_home.dart';
import 'screens/student/student_my_courses.dart';
import 'screens/student/student_message_center.dart';
import 'screens/student/student_password_screen.dart';

GoRouter buildRouter(AuthProvider auth) => GoRouter(
      initialLocation: '/login',
      refreshListenable: auth,
      redirect: (context, state) {
        if (auth.loading) return null;
        final loggedIn = auth.isLoggedIn;
        final loc = state.matchedLocation;
        final onLogin = loc == '/login';
        final onStudentPassword = loc == '/student/password';
        final mustForceStudentPassword =
            auth.isStudent && (auth.user?.mustChangePassword ?? false);
        final home = auth.isAdmin
            ? '/admin'
            : (auth.isStudent ? '/student' : '/teacher');

        if (!loggedIn && !onLogin) return '/login';
        if (loggedIn && onLogin) return home;
        if (loggedIn && mustForceStudentPassword && !onStudentPassword) {
          return '/student/password?force=true';
        }
        if (loggedIn && loc == '/') return home;
        return null;
      },
      routes: [
        GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
        GoRoute(path: '/student', builder: (_, __) => const StudentHome()),
        GoRoute(
          path: '/student/my-courses',
          builder: (_, __) => const StudentMyCoursesScreen(),
        ),
        GoRoute(
          path: '/student/messages',
          builder: (_, __) => const StudentMessageCenterScreen(),
        ),
        GoRoute(
          path: '/student/password',
          builder: (_, state) => StudentPasswordScreen(
            forceChange: state.uri.queryParameters['force'] == 'true',
          ),
        ),
        GoRoute(path: '/teacher', builder: (_, __) => const TeacherHome()),
        GoRoute(
          path: '/teacher/course-requests',
          builder: (_, __) => const CourseRequestCenterScreen(),
        ),
        GoRoute(
          path: '/teacher/course-requests/:id',
          builder: (_, state) => CourseRequestDetailScreen(
            requestId: int.parse(state.pathParameters['id']!),
          ),
        ),
        GoRoute(
          path: '/teacher/messages',
          builder: (_, __) => const TeacherMessageCenterScreen(),
        ),
        GoRoute(
          path: '/teacher/profile',
          builder: (_, __) => const TeacherProfileScreen(),
        ),
        GoRoute(
          path: '/teacher/students/:classId',
          builder: (_, state) => TeacherStudentListScreen(
            classId: int.parse(state.pathParameters['classId']!),
            className: state.uri.queryParameters['name'] ?? '',
          ),
        ),
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
        GoRoute(
          path: '/teacher/attendance-export',
          builder: (_, __) => const TeacherAttendanceExportScreen(),
        ),
        GoRoute(
          path: '/teacher/data-export',
          builder: (_, __) => const TeacherDataExportScreen(),
        ),
        GoRoute(path: '/admin', builder: (_, __) => const AdminHome()),
        GoRoute(
          path: '/admin/students',
          builder: (_, __) => const StudentListScreen(),
        ),
        GoRoute(
          path: '/admin/physical-tests',
          builder: (_, __) => const PhysicalListScreen(),
        ),
        GoRoute(
          path: '/admin/term-grades',
          builder: (_, __) => const GradeListScreen(),
        ),
        GoRoute(
          path: '/admin/attendance-export',
          builder: (_, __) => const AdminAttendanceExportScreen(),
        ),
        GoRoute(
          path: '/admin/data-export',
          builder: (_, __) => const AdminDataExportScreen(),
        ),
        GoRoute(
          path: '/admin/teacher-permissions',
          builder: (_, __) => const TeacherPermissionScreen(),
        ),
      ],
    );
