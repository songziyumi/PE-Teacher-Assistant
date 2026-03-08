# Real Device Teacher Debug

## 1) Start backend

Run backend on this machine first:

```powershell
mvn -q -DskipTests compile -Dmaven.repo.local=~/.m2/repository
mvn spring-boot:run
```

If `~/.m2/repository` is blocked in your environment, use a project-local repo:

```powershell
mvn -q -DskipTests compile -Dmaven.repo.local=.m2repo
mvn -Dmaven.repo.local=.m2repo spring-boot:run
```

## 2) Choose API base URL for phone

This app now supports `--dart-define=API_BASE_URL`.

- Current LAN IPv4 on this machine: `192.168.2.101`
- Example API base URL for a phone on same Wi-Fi: `http://192.168.2.101:8080`

Run Flutter on device:

```powershell
cd mobile
flutter run --dart-define=API_BASE_URL=http://192.168.2.101:8080
```

## 3) Teacher smoke checklist (mobile)

1. Login (`/api/auth/login`)
2. Home cards:
   `GET /api/teacher/course-requests/summary`
   `GET /api/teacher/messages/unread-count`
3. Message center:
   `GET /api/teacher/messages`
   `POST /api/teacher/messages/{id}/read`
4. Course request center:
   `GET /api/teacher/course-requests?status=PENDING`
   `GET /api/teacher/course-requests/{id}`
   `POST /api/teacher/course-requests/{id}/approve|reject`
5. Student edit:
   `GET /api/teacher/classes/{classId}/students`
   `GET /api/teacher/students/check-student-no`
   `PUT /api/teacher/students/{id}`

## 4) Quick API check from terminal

```powershell
$body = @{ username='<teacher_username>'; password='<teacher_password>' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/auth/login' -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/teacher/course-requests/summary' -Headers $headers
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/teacher/messages/unread-count' -Headers $headers
```

