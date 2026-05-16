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
          reject(new Error('登录已失效，请重新登录'));
          return;
        }
        if (!payload || typeof payload !== 'object') {
          reject(new Error(`接口返回异常 (${statusCode})`));
          return;
        }
        if (payload.code !== 200) {
          reject(new Error(payload.message || '请求失败'));
          return;
        }
        resolve(payload.data);
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络请求失败'));
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
          reject(new Error('头像上传返回异常'));
          return;
        }
        if (!payload || payload.code !== 200) {
          reject(new Error(payload && payload.message ? payload.message : '头像上传失败'));
          return;
        }
        resolve(payload.data || {});
      },
      fail(error) {
        reject(new Error(error.errMsg || '头像上传失败'));
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
  fetchTeacherProfile,
  fetchTeacherProfileStats,
  updateTeacherProfile,
  changeTeacherPassword,
  uploadTeacherProfilePhoto
};
