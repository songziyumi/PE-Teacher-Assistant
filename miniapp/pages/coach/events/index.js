const coachAuth = require('../../../utils/coach-auth.js');
Page({
  data: {},
  onShow() { if (!coachAuth.getToken()) wx.reLaunch({ url: '/pages/login/index' }); },
  goSignup() { wx.navigateTo({ url: '/pages/coach/signup/meets/index' }); },
  goLineup() { wx.navigateTo({ url: '/pages/coach/events/lineup/index' }); }
});
