const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

Page({
  data: {
    loading: true,
    classes: [],
    errorMessage: ''
  },

  onShow() {
    if (!auth.getToken()) {
      wx.reLaunch({
        url: '/pages/login/index'
      });
      return;
    }
    this.loadClasses();
  },

  async loadClasses() {
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const app = getApp();
      const home = app.globalData.home || (await api.fetchHome());
      app.globalData.home = home;
      this.setData({
        loading: false,
        classes: home.classes || []
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '加载班级失败'
      });
    }
  },

  openClass(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className;
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/attendance-edit/index?classId=${classId}&className=${encodeURIComponent(className || '')}`
    });
  }
});
