const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');
Page({
  data: { loading: true, errorMessage: '', meetId: null, eventId: null, signupId: null, view: null, athletes: [], officials: [], availableAthletes: [], availableOfficials: [] },
  onLoad(options) { if (!coachAuth.getToken()) return wx.reLaunch({ url: '/pages/login/index' }); this.setData({ meetId: Number(options.meetId), eventId: Number(options.eventId) }); this.loadData(); },
  async resolveTeamId() {
    const user = await coachApi.fetchMe();
    let teamId = Number(user && (user.teamScopeId || user.teamId || (user.team && user.team.id))) || null;
    if (!teamId) { const athletes = await coachApi.fetchAthletes(); teamId = Number(athletes && athletes[0] && athletes[0].teamId) || null; }
    return teamId;
  },
  async loadData() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const meetId = this.data.meetId; const eventId = this.data.eventId;
      const [meets, events, signups, athletes, officials] = await Promise.all([coachApi.fetchMeets(), coachApi.fetchMeetEvents(meetId), coachApi.fetchSignups({ meetId, eventId }), coachApi.fetchAthletes(), coachApi.fetchOfficials()]);
      const meet = (meets || []).find((item) => Number(item.id) === meetId); const event = (events || []).find((item) => Number(item.id) === eventId);
      let signup = (signups || []).find((item) => Number(item.eventId) === eventId);
      if (!signup) signup = await coachApi.createSignup({ meetId, eventId, teamId: await this.resolveTeamId(), signupType: '代表队', status: 'DRAFT' });
      const [savedAthletes, savedOfficials] = await Promise.all([coachApi.fetchSignupAthletes(signup.id), coachApi.fetchSignupOfficials(signup.id)]);
      const selectedA = (savedAthletes || []).map((item) => Object.assign({}, item, { id: item.athleteId, checked: true }));
      const selectedO = (savedOfficials || []).map((item) => Object.assign({}, item, { id: item.officialId, checked: true }));
      const selectedAIds = selectedA.map((item) => Number(item.id)); const selectedOIds = selectedO.map((item) => Number(item.id));
      const status = { 草稿: 'DRAFT', 已驳回: 'REJECTED', 已提交: 'SUBMITTED', 已通过: 'APPROVED', 已锁定: 'LOCKED' }[signup.status] || signup.status;
      const statusText = { DRAFT: '草稿', REJECTED: '已驳回', SUBMITTED: '已提交', APPROVED: '已通过', LOCKED: '已锁定' }[status] || status || '';
      this.setData({ loading: false, signupId: signup.id, view: Object.assign({}, signup, { status, meetName: meet && meet.meetName, eventName: event && event.eventName, statusText }), athletes: selectedA, officials: selectedO, availableAthletes: (athletes || []).map((item) => Object.assign({}, item, { checked: selectedAIds.indexOf(Number(item.id)) >= 0, jerseyNo: (selectedA.find((row) => Number(row.id) === Number(item.id)) || {}).jerseyNo || '' })), availableOfficials: (officials || []).map((item) => Object.assign({}, item, { checked: selectedOIds.indexOf(Number(item.id)) >= 0 })) });
    } catch (error) { this.setData({ loading: false, errorMessage: error.message || '加载失败' }); }
  },
  toggleAthlete(event) { const id = Number(event.currentTarget.dataset.id); const idx = this.data.availableAthletes.findIndex((item) => Number(item.id) === id); if (idx >= 0) this.setData({ [`availableAthletes[${idx}].checked`]: !this.data.availableAthletes[idx].checked }); },
  toggleOfficial(event) { const id = Number(event.currentTarget.dataset.id); const idx = this.data.availableOfficials.findIndex((item) => Number(item.id) === id); if (idx >= 0) this.setData({ [`availableOfficials[${idx}].checked`]: !this.data.availableOfficials[idx].checked }); },
  stopTap() {},
  onJerseyInput(event) { const id = Number(event.currentTarget.dataset.id); const idx = this.data.availableAthletes.findIndex((item) => Number(item.id) === id); if (idx >= 0) this.setData({ [`availableAthletes[${idx}].jerseyNo`]: event.detail.value }); },
  async saveRoster(options = {}) { try { const a = this.data.availableAthletes.filter((item) => item.checked).map((item, index) => ({ athleteId: item.id, jerseyNo: item.jerseyNo || null, captain: false, sortNo: index + 1 })); const o = this.data.availableOfficials.filter((item) => item.checked).map((item, index) => ({ officialId: item.id, sortNo: index + 1 })); await Promise.all([coachApi.saveSignupAthletes(this.data.signupId, a), coachApi.saveSignupOfficials(this.data.signupId, o)]); if (!options.silent) wx.showToast({ title: '名单已保存', icon: 'success' }); await this.loadData(); } catch (error) { if (!options.silent) this.showError(error.message); throw error; } },
  async submit() { try { await this.saveRoster({ silent: true }); await coachApi.submitSignup(this.data.signupId); wx.showToast({ title: '报名已提交', icon: 'success' }); await this.loadData(); } catch (error) { this.showError(error.message); } },
  async withdraw() { try { await coachApi.withdrawSignup(this.data.signupId); wx.showToast({ title: '已撤回', icon: 'success' }); await this.loadData(); } catch (error) { this.showError(error.message); } },
  showError(message) { wx.showModal({ title: '提示', content: message || '操作失败', showCancel: false }); }
});
