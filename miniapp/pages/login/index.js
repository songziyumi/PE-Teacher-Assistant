const api = require('../../utils/api.js');

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    errorMessage: ''
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

    if (!username || !password) {
      this.setData({ errorMessage: '请输入账号和密码' });
      return;
    }

    this.setData({
      loading: true,
      errorMessage: ''
    });

    try {
      await api.login(username, password);
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
