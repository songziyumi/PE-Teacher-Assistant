const api = require('../../utils/api.js');
const coachApi = require('../../utils/coach-api.js');

Page({
  data: {
    role: 'teacher',
    username: '',
    password: '',
    loading: false,
    errorMessage: ''
  },

  onRoleChange(event) {
    const role = event.currentTarget.dataset.role;
    this.setData({
      role,
      errorMessage: ''
    });
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
      if (this.data.role === 'coach') {
        await coachApi.login(username, password);
        wx.reLaunch({
          url: '/pages/coach/home/index'
        });
      } else {
        await api.login(username, password);
        wx.reLaunch({
          url: '/pages/home/index'
        });
      }
    } catch (error) {
      this.setData({
        errorMessage: error.message || '登录失败'
      });
    } finally {
      this.setData({ loading: false });
    }
  }
});
