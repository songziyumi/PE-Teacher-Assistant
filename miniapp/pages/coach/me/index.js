const coachApi = require('../../../utils/coach-api.js');
const coachAuth = require('../../../utils/coach-auth.js');
Page({ data: { user: {} }, onShow() { if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' }); this.setData({ user: coachAuth.getUser() || {} }); }, goTeam() { wx.navigateTo({ url: '/pages/coach/team/profile/index' }); }, goAccount() { wx.navigateTo({ url: '/pages/coach/team/account/index' }); }, logout() { coachAuth.clearAll(); wx.reLaunch({ url: '/pages/login/index' }); } });
