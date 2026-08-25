const coachApi = require('../../../utils/coach-api.js');
const coachAuth = require('../../../utils/coach-auth.js');

Page({
  data: {
    loading: true,
    errorMessage: '',
    user: null
  },

  onShow() {
    if (!coachAuth.getToken()) {
      wx.reLaunch({ url: '/pages/login/index' });
      return;
    }
    this.loadData();
  },

  async loadData() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const me = await coachApi.fetchMe();
      this.setData({
        loading: false,
        user: me || null
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '加载失败'
      });
    }
  },

  goLineup(event) {
    const matchId = event.currentTarget.dataset.matchId;
    if (!matchId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/coach/lineup/index?matchId=${matchId}`
    });
  },

  goAthletes() {
    wx.navigateTo({ url: '/pages/coach/athletes/list/index' });
  },

  goOfficials() {
    wx.navigateTo({ url: '/pages/coach/officials/list/index' });
  },

  goEvents() { wx.navigateTo({ url: '/pages/coach/events/index' }); },
  goMe() { wx.navigateTo({ url: '/pages/coach/me/index' }); },

  handleLogout() {
    coachAuth.clearAll();
    wx.reLaunch({ url: '/pages/login/index' });
  }
});
