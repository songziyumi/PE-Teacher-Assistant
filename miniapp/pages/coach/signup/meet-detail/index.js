const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');
Page({
  data: { loading: true, errorMessage: '', meet: {}, events: [] },
  onLoad(options) { if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' }); this.setData({ meetId: Number(options.meetId) }); this.loadData(); },
  async loadData() { try { const meet = await coachApi.fetchMeets().then((items) => (items || []).find((item) => Number(item.id) === this.data.meetId)); const events = await coachApi.fetchMeetEvents(this.data.meetId); this.setData({ loading: false, meet: meet || {}, events: (events || []).filter((item) => item.enabled !== false) }); } catch (e) { this.setData({ loading: false, errorMessage: e.message || '加载失败' }); } }
});
