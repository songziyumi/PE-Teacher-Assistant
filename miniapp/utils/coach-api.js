const config = require('./config.js');
const coachAuth = require('./coach-auth.js');

function getBaseUrl() {
  return config.COACH_BASE_URL;
}

function request(options) {
  const token = coachAuth.getToken();
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
        if (statusCode >= 200 && statusCode < 300) {
          resolve(payload && payload.data !== undefined ? payload.data : payload);
          return;
        }
        if (statusCode === 401) {
          coachAuth.clearAll();
        }
        const message = (payload && payload.data && payload.data.error)
          || (payload && payload.message)
          || `请求失败 (${statusCode})`;
        reject(new Error(message));
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络请求失败'));
      }
    });
  });
}

function login(username, password) {
  return request({
    path: '/api/miniapp/auth/login',
    method: 'POST',
    data: {
      username,
      password
    }
  }).then((data) => {
    coachAuth.saveToken(data.token || '');
    coachAuth.saveUser(data.user || null);
    return data;
  });
}

function fetchMe() {
  return request({
    path: '/api/miniapp/auth/me'
  }).then((data) => {
    coachAuth.saveUser(data || null);
    return data;
  });
}

function fetchTeamMatches() {
  return request({
    path: '/api/signups/ball-sports/matches'
  });
}

function fetchLineup(matchId) {
  return request({
    path: `/api/signups/ball-sports/matches/${matchId}/lineup`
  });
}

function submitLineup(matchId, athleteIds) {
  return request({
    path: `/api/signups/ball-sports/matches/${matchId}/lineup`,
    method: 'PUT',
    data: {
      athleteIds
    }
  });
}

function fetchAthletes() {
  return request({
    path: '/api/athletes'
  });
}

function fetchAthlete(id) {
  return request({
    path: `/api/athletes/${id}`
  });
}

function createAthlete(data) {
  return request({
    path: '/api/athletes',
    method: 'POST',
    data
  });
}

function updateAthlete(id, data) {
  return request({
    path: `/api/athletes/${id}`,
    method: 'PUT',
    data
  });
}

function deleteAthlete(id) {
  return request({
    path: `/api/athletes/${id}`,
    method: 'DELETE'
  });
}

function batchDeleteAthletes(athleteIds) {
  return request({
    path: '/api/athletes/batch-delete',
    method: 'DELETE',
    data: {
      athleteIds
    }
  });
}

function updateAthleteStatus(id, data) {
  return request({
    path: `/api/athletes/${id}/status`,
    method: 'PUT',
    data
  });
}

function uploadPhoto(filePath) {
  const token = coachAuth.getToken();
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${getBaseUrl()}/api/uploads/photo`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(response) {
        const statusCode = response.statusCode;
        let payload = null;
        try {
          payload = JSON.parse(response.data || '{}');
        } catch (e) {
          payload = null;
        }
        if (statusCode >= 200 && statusCode < 300) {
          resolve(payload && payload.data !== undefined ? payload.data : payload);
          return;
        }
        if (statusCode === 401) {
          coachAuth.clearAll();
        }
        const message = (payload && payload.data && payload.data.error)
          || (payload && payload.message)
          || `上传失败 (${statusCode})`;
        reject(new Error(message));
      },
      fail(error) {
        reject(new Error(error.errMsg || '上传失败'));
      }
    });
  });
}

module.exports = {
  getBaseUrl,
  request,
  login,
  fetchMe,
  fetchTeamMatches,
  fetchLineup,
  submitLineup,
  fetchAthletes,
  fetchAthlete,
  createAthlete,
  updateAthlete,
  deleteAthlete,
  batchDeleteAthletes,
  updateAthleteStatus,
  uploadPhoto
};
