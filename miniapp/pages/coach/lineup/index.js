const coachApi = require('../../../utils/coach-api.js');

Page({
  data: {
    matchId: null,
    loading: true,
    submitting: false,
    errorMessage: '',
    message: '',
    messageTone: 'info',
    view: null,
    selectedCount: 0
  },

  onLoad(options) {
    const matchId = options && options.matchId ? Number(options.matchId) : null;
    this.setData({ matchId });
    this.loadLineup();
  },

  async loadLineup() {
    if (!this.data.matchId) {
      this.setData({ loading: false, errorMessage: '缺少比赛参数' });
      return;
    }
    this.setData({ loading: true, errorMessage: '', message: '' });
    try {
      const raw = await coachApi.fetchLineup(this.data.matchId);
      const starterIds = raw.starterAthleteIds || [];
      const roster = (raw.roster || []).map((item) => Object.assign({}, item, {
        checked: starterIds.indexOf(item.athleteId) >= 0
      }));
      this.setData({
        loading: false,
        view: Object.assign({}, raw, { roster }),
        selectedCount: roster.filter((item) => item.checked).length
      });
    } catch (error) {
      this.setData({ loading: false, errorMessage: error.message || '加载失败' });
    }
  },

  onToggleAthlete(event) {
    const athleteId = Number(event.currentTarget.dataset.athleteId);
    const view = this.data.view;
    if (!view) {
      return;
    }
    const starterCount = view.starterCount || 0;
    const roster = view.roster || [];
    const idx = roster.findIndex((item) => item.athleteId === athleteId);
    if (idx < 0) {
      return;
    }
    if (roster[idx].checked) {
      this.setData({
        [`view.roster[${idx}].checked`]: false,
        selectedCount: Math.max(0, this.data.selectedCount - 1),
        message: ''
      });
      return;
    }
    if (starterCount > 0 && this.data.selectedCount >= starterCount) {
      this.showMessage(`最多选择 ${starterCount} 名首发运动员。`, 'error');
      return;
    }
    this.setData({
      [`view.roster[${idx}].checked`]: true,
      selectedCount: this.data.selectedCount + 1,
      message: ''
    });
  },

  async handleSubmit() {
    if (this.data.submitting || !this.data.view) {
      return;
    }
    const view = this.data.view;
    const starterCount = view.starterCount || 0;
    const athleteIds = (view.roster || []).filter((item) => item.checked).map((item) => item.athleteId);
    if (starterCount > 0 && athleteIds.length !== starterCount) {
      this.showMessage(`请选择 ${starterCount} 名首发运动员。`, 'error');
      return;
    }
    this.setData({ submitting: true, message: '' });
    try {
      await coachApi.submitLineup(this.data.matchId, athleteIds);
      this.showMessage('首发名单已提交。', 'success');
      setTimeout(() => {
        wx.navigateBack();
      }, 700);
    } catch (error) {
      this.setData({ submitting: false });
      this.showMessage(error.message || '提交失败', 'error');
    }
  },

  showMessage(text, tone) {
    this.setData({
      message: text || '',
      messageTone: tone || 'info'
    });
  }
});
