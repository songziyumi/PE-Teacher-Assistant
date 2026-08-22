const coachApi = require('../../../utils/coach-api.js');
const coachAuth = require('../../../utils/coach-auth.js');

function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  const text = String(value);
  const replaced = text.indexOf('T') >= 0 ? text.replace('T', ' ') : text;
  return replaced.slice(0, 16);
}

Page({
  data: {
    loading: true,
    errorMessage: '',
    user: null,
    matches: []
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
      const matches = await coachApi.fetchTeamMatches();
      const list = (matches || []).map((item) => Object.assign({}, item, {
        opponent: item.currentTeamSide === 'A' ? (item.teamBName || '-') : (item.teamAName || '-'),
        startAtText: formatDateTime(item.startAt)
      }));
      this.setData({
        loading: false,
        user: me || null,
        matches: list
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

  handleLogout() {
    coachAuth.clearAll();
    wx.reLaunch({ url: '/pages/login/index' });
  }
});
