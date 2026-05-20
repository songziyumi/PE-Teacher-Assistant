const config = require('./config');
const auth = require('./auth');

function getBaseUrl() {
  return config.normalizeBaseUrl(auth.getBaseUrl());
}

function request(options) {
  const token = auth.getToken();
  const headers = Object.assign({}, options.headers || {});
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.json !== false) {
    headers['Content-Type'] = 'application/json';
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${getBaseUrl()}${options.path}`,
      method: options.method || 'GET',
      data: options.data,
      header: headers,
      success(response) {
        const statusCode = response.statusCode;
        const payload = response.data;
        if (statusCode === 401) {
          auth.clearAll();
          reject(new Error('\u767b\u5f55\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55'));
          return;
        }
        if (!payload || typeof payload !== 'object') {
          reject(new Error(`\u63a5\u53e3\u8fd4\u56de\u5f02\u5e38 (${statusCode})`));
          return;
        }
        if (payload.code !== 200) {
          reject(new Error(payload.message || '\u8bf7\u6c42\u5931\u8d25'));
          return;
        }
        resolve(payload.data);
      },
      fail(error) {
        reject(new Error(error.errMsg || '\u7f51\u7edc\u8bf7\u6c42\u5931\u8d25'));
      }
    });
  });
}

function login(baseUrl, username, password) {
  const normalizedBaseUrl = config.normalizeBaseUrl(baseUrl);
  auth.saveBaseUrl(normalizedBaseUrl);
  return request({
    path: '/api/miniapp/auth/login',
    method: 'POST',
    data: {
      username,
      password
    }
  }).then((data) => {
    auth.saveToken(data.token || '');
    auth.saveUser(data.user || null);
    return data;
  });
}

function fetchMe() {
  return request({
    path: '/api/miniapp/auth/me'
  }).then((data) => {
    auth.saveUser(data.user || null);
    return data;
  });
}

function fetchHome() {
  return request({
    path: '/api/miniapp/home'
  });
}

function fetchTeacherClassStudents(classId, keyword, studentStatus, page, size) {
  const params = [];
  params.push(`page=${page || 0}`);
  params.push(`size=${size || 50}`);
  if (keyword) {
    params.push(`keyword=${encodeURIComponent(keyword)}`);
  }
  if (studentStatus) {
    params.push(`studentStatus=${encodeURIComponent(studentStatus)}`);
  }
  return request({
    path: `/api/miniapp/teacher/classes/${classId}/students?${params.join('&')}`
  });
}

function fetchTeacherAttendance(classId, date) {
  return request({
    path: `/api/teacher/attendance?classId=${classId}&date=${encodeURIComponent(date)}`
  });
}

function saveTeacherAttendance(classId, date, records) {
  return request({
    path: '/api/teacher/attendance/save-batch',
    method: 'POST',
    data: {
      classId,
      date,
      records
    }
  });
}

function fetchTeacherPhysicalTests(classId, academicYear, semester) {
  return request({
    path: `/api/teacher/physical-tests?classId=${classId}&academicYear=${encodeURIComponent(academicYear)}&semester=${encodeURIComponent(semester)}`
  });
}

function fetchTeacherPhysicalDetail(classId, studentId, academicYear, semester) {
  return request({
    path: `/api/teacher/physical-tests/${studentId}?classId=${classId}&academicYear=${encodeURIComponent(academicYear)}&semester=${encodeURIComponent(semester)}`
  });
}

function saveTeacherPhysicalTests(items) {
  return request({
    path: '/api/teacher/physical-tests/save-batch',
    method: 'POST',
    data: items
  });
}

function fetchTeacherTermGrades(classId, academicYear, semester) {
  return request({
    path: `/api/teacher/term-grades?classId=${classId}&academicYear=${encodeURIComponent(academicYear)}&semester=${encodeURIComponent(semester)}`
  });
}

function saveTeacherTermGrades(classId, academicYear, semester, items) {
  return request({
    path: '/api/teacher/term-grades/save-batch',
    method: 'POST',
    data: {
      classId,
      academicYear,
      semester,
      items
    }
  });
}

function fetchTeacherProfile() {
  return request({
    path: '/api/teacher/profile'
  });
}

function fetchTeacherProfileStats() {
  return request({
    path: '/api/teacher/profile/stats'
  });
}

function fetchTeacherStudentAttendanceHistory(studentId, days) {
  return request({
    path: `/api/teacher/students/${studentId}/attendance-history?days=${days || 90}`
  });
}

function checkTeacherStudentNo(studentNo, excludeId) {
  const params = [`studentNo=${encodeURIComponent(studentNo || '')}`];
  if (excludeId) {
    params.push(`excludeId=${excludeId}`);
  }
  return request({
    path: `/api/teacher/students/check-student-no?${params.join('&')}`
  });
}

function updateTeacherStudent(studentId, payload) {
  return request({
    path: `/api/teacher/students/${studentId}`,
    method: 'PUT',
    data: payload
  });
}

function updateTeacherProfile(payload) {
  return request({
    path: '/api/teacher/profile',
    method: 'PUT',
    data: payload
  });
}

function changeTeacherPassword(oldPassword, newPassword) {
  return request({
    path: '/api/teacher/password/change',
    method: 'POST',
    data: {
      oldPassword,
      newPassword
    }
  });
}

function uploadTeacherProfilePhoto(filePath) {
  const token = auth.getToken();
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${getBaseUrl()}/api/teacher/profile/photo`,
      filePath,
      name: 'photo',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(response) {
        let payload = null;
        try {
          payload = JSON.parse(response.data);
        } catch (error) {
          reject(new Error('\u5934\u50cf\u4e0a\u4f20\u8fd4\u56de\u5f02\u5e38'));
          return;
        }
        if (!payload || payload.code !== 200) {
          reject(new Error(payload && payload.message ? payload.message : '\u5934\u50cf\u4e0a\u4f20\u5931\u8d25'));
          return;
        }
        resolve(payload.data || {});
      },
      fail(error) {
        reject(new Error(error.errMsg || '\u5934\u50cf\u4e0a\u4f20\u5931\u8d25'));
      }
    });
  });
}

module.exports = {
  getBaseUrl,
  request,
  login,
  fetchMe,
  fetchHome,
  fetchTeacherClassStudents,
  fetchTeacherAttendance,
  saveTeacherAttendance,
  fetchTeacherPhysicalTests,
  fetchTeacherPhysicalDetail,
  saveTeacherPhysicalTests,
  fetchTeacherTermGrades,
  saveTeacherTermGrades,
  fetchTeacherStudentAttendanceHistory,
  checkTeacherStudentNo,
  updateTeacherStudent,
  fetchTeacherProfile,
  fetchTeacherProfileStats,
  updateTeacherProfile,
  changeTeacherPassword,
  uploadTeacherProfilePhoto
};
