const coachApi = require('../../../../../utils/coach-api.js');
const coachAuth = require('../../../../../utils/coach-auth.js');
Page({
  data: { loading: true, errorMessage: '', meets: [] },
  onShow() { if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' }); this.loadData(); },
  async loadData() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const raw = await coachApi.fetchMeets();
      const meets = [];
      for (const meet of (raw || [])) {
        const events = await coachApi.fetchMeetEvents(meet.id);
        meets.push(Object.assign({}, meet, { events: (events || []).filter((item) => item.enabled !== false).map((item) => Object.assign({}, item, { meetId: meet.id, id2: item.id, signupStatus: '进入报名' })) }));
      }
      this.setData({ loading: false, meets });
    } catch (error) { this.setData({ loading: false, errorMessage: error.message || '加载失败' }); }
  },
  openEvent(event) {
    const { meetId, eventId } = event.currentTarget.dataset;
    wx.navigateTo({ url: `/pages/coach/signup/detail/index?meetId=${meetId}&eventId=${eventId}` });
  }
});
