const api = require('../../utils/api');
const auth = require('../../utils/auth');
const config = require('../../utils/config');

Page({
  data: {
    baseUrl: '',
    username: '',
    password: '',
    loading: false,
    errorMessage: ''
  },

  onLoad() {
    this.setData({
      baseUrl: auth.getBaseUrl() || config.DEFAULT_BASE_URL
    });
  },

  onBaseUrlInput(event) {
    this.setData({ baseUrl: event.detail.value });
  },

  onUsernameInput(event) {
    this.setData({ username: event.detail.value });
  },

  onPasswordInput(event) {
    this.setData({ password: event.detail.value });
  },

  async handleLogin() {
    if (this.data.loading) {
      return;
    }
    const username = (this.data.username || '').trim();
    const password = this.data.password || '';
    const baseUrl = (this.data.baseUrl || '').trim();

    if (!baseUrl) {
      this.setData({ errorMessage: '请填写服务器地址' });
      return;
    }
    if (!username || !password) {
      this.setData({ errorMessage: '请输入账号和密码' });
      return;
    }

    this.setData({
      loading: true,
      errorMessage: ''
    });

    try {
      await api.login(baseUrl, username, password);
      wx.reLaunch({
        url: '/pages/home/index'
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || '登录失败'
      });
    } finally {
      this.setData({ loading: false });
    }
  }
});
