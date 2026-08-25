const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');
Page({
  data: { loading: true, saving: false, account: {}, teamId: null, form: {} },
  onShow() { if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' }); this.loadData(); },
  async loadData() { try { const user = await coachApi.fetchMe(); let id = Number(user && (user.teamScopeId || user.teamId || (user.team && user.team.id))) || null; if (!id) { const athletes = await coachApi.fetchAthletes(); id = Number(athletes && athletes[0] && athletes[0].teamId) || null; } this.setData({ teamId: id, account: await coachApi.fetchTeamAccount(id), loading: false }); } catch (e) { this.setData({ loading: false }); wx.showModal({ title: '提示', content: e.message || '加载失败', showCancel: false }); } },
  onInput(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }); },
  async save() { const f = this.data.form; if (!f.currentPassword || !f.newPassword || f.newPassword.length < 6 || f.newPassword !== f.confirmPassword) return wx.showToast({ title: '请检查密码填写', icon: 'none' }); this.setData({ saving: true }); try { await coachApi.changePassword(f.currentPassword, f.newPassword); wx.showToast({ title: '密码已修改，请重新登录', icon: 'success' }); setTimeout(() => { coachAuth.clearAll(); wx.reLaunch({ url: '/pages/login/index' }); }, 700); } catch (e) { this.setData({ saving: false }); wx.showModal({ title: '提示', content: e.message || '修改失败', showCancel: false }); } }
});
