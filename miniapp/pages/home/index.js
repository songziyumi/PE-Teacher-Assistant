const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    loading: true,
    errorMessage: '',
    home: null,
    user: null,
    isStudent: false,
    isTeacher: false
  },

  onShow() {
    if (!auth.getToken()) {
      wx.reLaunch({
        url: '/pages/login/index'
      });
      return;
    }
    this.loadHome();
  },

  async loadHome() {
    this.setData({
      loading: true,
      errorMessage: ''
    });

    try {
      const [me, home] = await Promise.all([
        api.fetchMe(),
        api.fetchHome()
      ]);
      const user = me.user || null;
      const role = user ? user.role : '';
      getApp().globalData.user = user;
      getApp().globalData.home = home;
      this.setData({
        loading: false,
        home,
        user,
        isStudent: role === 'STUDENT',
        isTeacher: role !== 'STUDENT'
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '加载首页失败'
      });
    }
  },

  async handleRefresh() {
    await this.loadHome();
  },

  goTeacherAttendance() {
    wx.navigateTo({
      url: '/pages/teacher/attendance/index'
    });
  },

  goTeacherAttendanceForClass(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className || '';
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/attendance-edit/index?classId=${classId}&className=${encodeURIComponent(className)}`
    });
  },

  goTeacherProfile() {
    wx.navigateTo({
      url: '/pages/teacher/profile/index'
    });
  },

  showTodoFeature(event) {
    const label = event.currentTarget.dataset.label || '该功能';
    wx.showToast({
      title: `${label}开发中`,
      icon: 'none'
    });
  },

  handleLogout() {
    auth.clearAll();
    getApp().globalData.user = null;
    getApp().globalData.home = null;
    wx.reLaunch({
      url: '/pages/login/index'
    });
  }
});
