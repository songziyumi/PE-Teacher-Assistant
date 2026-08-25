const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

Page({
  data: { loading: true, errorMessage: '', matches: [] },
  onShow() {
    if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' });
    this.loadData();
  },
  async loadData() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const matches = await coachApi.fetchTeamMatches();
      this.setData({ loading: false, matches: (matches || []).map((item) => Object.assign({}, item, {
        opponent: item.currentTeamSide === 'A' ? (item.teamBName || '-') : (item.teamAName || '-'),
        startAtText: formatDateTime(item.startAt)
      })) });
    } catch (error) {
      this.setData({ loading: false, errorMessage: error.message || '加载失败' });
    }
  },
  goLineup(event) {
    const matchId = event.currentTarget.dataset.matchId;
    if (matchId) wx.navigateTo({ url: `/pages/coach/lineup/index?matchId=${matchId}` });
  }
});
