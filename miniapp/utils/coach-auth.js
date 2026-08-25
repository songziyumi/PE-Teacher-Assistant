const COACH_TOKEN_KEY = 'coach_token';
const COACH_USER_KEY = 'coach_user';

function saveToken(token) {
  wx.setStorageSync(COACH_TOKEN_KEY, token || '');
}

function getToken() {
  return wx.getStorageSync(COACH_TOKEN_KEY) || '';
}

function clearToken() {
  wx.removeStorageSync(COACH_TOKEN_KEY);
}

function saveUser(user) {
  wx.setStorageSync(COACH_USER_KEY, user || null);
}

function getUser() {
  return wx.getStorageSync(COACH_USER_KEY) || null;
}

function clearUser() {
  wx.removeStorageSync(COACH_USER_KEY);
}

function clearAll() {
  clearToken();
  clearUser();
}

module.exports = {
  saveToken,
  getToken,
  clearToken,
  saveUser,
  getUser,
  clearUser,
  clearAll
};
