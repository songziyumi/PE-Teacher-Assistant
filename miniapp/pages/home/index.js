const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    loading: true,
    errorMessage: '',
    home: null,
    user: null,
    isStudent: false,
    isTeacher: false,
    text: {
      workbench: '\u5de5\u4f5c\u53f0',
      profile: '\u4e2a\u4eba\u4e3b\u9875',
      logout: '\u9000\u51fa',
      loading: '\u6b63\u5728\u52a0\u8f7d\u9996\u9875\u6570\u636e...',
      loadFailed: '\u52a0\u8f7d\u5931\u8d25',
      reload: '\u91cd\u65b0\u52a0\u8f7d',
      studentSummary: '\u5b66\u751f\u7aef\u6458\u8981',
      currentCourseCount: '\u5f53\u524d\u8bfe\u7a0b\u6570',
      confirmedCourseCount: '\u5df2\u786e\u8ba4\u8bfe\u7a0b',
      pendingSelectionCount: '\u5f85\u5904\u7406\u5fd7\u613f',
      unreadMessageCount: '\u672a\u8bfb\u6d88\u606f',
      teacherSummary: '\u6559\u5e08\u7aef\u6458\u8981',
      classCount: '\u73ed\u7ea7\u6570',
      pendingCourseRequestCount: '\u5f85\u5ba1\u6279',
      currentEvent: '\u5f53\u524d\u6d3b\u52a8',
      eventName: '\u540d\u79f0',
      eventStatus: '\u72b6\u6001',
      myClasses: '\u6211\u7684\u73ed\u7ea7',
      noGrade: '\u672a\u5206\u5e74\u7ea7',
      classType: '\u73ed\u7ea7',
      attendanceEntry: '\u8003\u52e4\u5f55\u5165',
      studentList: '\u5b66\u751f\u5217\u8868',
      physicalEntry: '\u4f53\u8d28\u5f55\u5165',
      gradeEntry: '\u6210\u7ee9\u5f55\u5165',
      recentMessages: '\u6700\u8fd1\u6d88\u606f',
      unnamedMessage: '\u672a\u547d\u540d\u6d88\u606f',
      system: '\u7cfb\u7edf',
      noRecentMessages: '\u6682\u65e0\u6700\u8fd1\u6d88\u606f\u3002',
      recentActivities: '\u6700\u8fd1\u64cd\u4f5c',
      todoFeature: '\u8be5\u529f\u80fd',
      developing: '\u5f00\u53d1\u4e2d'
    }
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
        errorMessage: error.message || '\u52a0\u8f7d\u9996\u9875\u5931\u8d25'
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

  goTeacherStudentList(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className || '';
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/student-list/index?classId=${classId}&className=${encodeURIComponent(className)}`
    });
  },

  goTeacherProfile() {
    wx.navigateTo({
      url: '/pages/teacher/profile/index'
    });
  },

  showTodoFeature(event) {
    const label = event.currentTarget.dataset.label || this.data.text.todoFeature;
    wx.showToast({
      title: `${label}${this.data.text.developing}`,
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
