const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');
Page({
  data: { loading: true, saving: false, errorMessage: '', teamId: null, form: {} },
  onShow() { if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' }); this.loadData(); },
  async resolveTeamId() { const user = await coachApi.fetchMe(); let id = Number(user && (user.teamScopeId || user.teamId || (user.team && user.team.id))) || null; if (!id) { const athletes = await coachApi.fetchAthletes(); id = Number(athletes && athletes[0] && athletes[0].teamId) || null; } return id; },
  async loadData() { this.setData({ loading: true, errorMessage: '' }); try { const teamId = await this.resolveTeamId(); const team = await coachApi.fetchTeam(teamId); this.setData({ loading: false, teamId, form: team || {} }); } catch (e) { this.setData({ loading: false, errorMessage: e.message || '加载失败' }); } },
  onInput(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }); },
  async save() { if (this.data.saving) return; const f = this.data.form; this.setData({ saving: true }); try { const saved = await coachApi.updateTeam(this.data.teamId, { teamName: f.teamName, teamShortName: f.teamShortName || null, schoolName: f.schoolName || null, schoolType: f.schoolType || '中小学校', cityName: f.cityName || null, districtName: f.districtName || null, contactName: f.contactName || null, contactPhone: f.contactPhone || null, remark: f.remark || null }); this.setData({ form: saved, saving: false }); wx.showToast({ title: '已保存', icon: 'success' }); } catch (e) { this.setData({ saving: false }); wx.showModal({ title: '提示', content: e.message || '保存失败', showCancel: false }); } }
});
