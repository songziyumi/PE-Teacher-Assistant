const api = require('../../../utils/api.js');
const auth = require('../../../utils/auth.js');

Page({
  data: {
    loading: true,
    classes: [],
    errorMessage: '',
    text: {
      title: '\u6559\u5e08\u70b9\u540d',
      subtitle: '\u5148\u9009\u62e9\u4e00\u4e2a\u4f60\u8d1f\u8d23\u7684\u73ed\u7ea7\uff0c\u518d\u8fdb\u5165\u5f53\u65e5\u8003\u52e4\u767b\u8bb0\u3002',
      loading: '\u6b63\u5728\u52a0\u8f7d\u73ed\u7ea7\u5217\u8868...',
      empty: '\u5f53\u524d\u8d26\u53f7\u4e0b\u8fd8\u6ca1\u6709\u53ef\u70b9\u540d\u7684\u73ed\u7ea7\u3002',
      sectionTitle: '\u6211\u7684\u73ed\u7ea7',
      noGrade: '\u672a\u5206\u5e74\u7ea7',
      classType: '\u73ed\u7ea7',
      metaSeparator: ' \u00b7 ',
      loadFailed: '\u52a0\u8f7d\u73ed\u7ea7\u5931\u8d25'
    }
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
        errorMessage: error.message || this.data.text.loadFailed
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
