const TOKEN_KEY = 'miniapp_token';
const USER_KEY = 'miniapp_user';

function saveToken(token) {
  wx.setStorageSync(TOKEN_KEY, token || '');
}

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || '';
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY);
}

function saveUser(user) {
  wx.setStorageSync(USER_KEY, user || null);
}

function getUser() {
  return wx.getStorageSync(USER_KEY) || null;
}

function clearUser() {
  wx.removeStorageSync(USER_KEY);
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
