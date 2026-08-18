const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');

Page({
  data: {
    loading: true,
    errorMessage: '',
    keyword: '',
    athletes: [],
    filtered: [],
    selectMode: false,
    selectedIds: [],
    busyId: null
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
      const athletes = await coachApi.fetchAthletes();
      const list = (athletes || []).map((item) => this.decorate(item));
      this.setData({ loading: false, athletes: list });
      this.applyFilter();
    } catch (error) {
      this.setData({ loading: false, errorMessage: error.message || '加载失败' });
    }
  },

  decorate(item) {
    return Object.assign({}, item, {
      projectsText: (item.projects && item.projects.length ? item.projects.join('、') : '-'),
      statusText: item.enabled === false ? '已停用' : '有效'
    });
  },

  applyFilter() {
    const keyword = (this.data.keyword || '').trim().toLowerCase();
    const selectedIds = this.data.selectedIds || [];
    const athletes = this.data.athletes || [];
    const base = keyword
      ? athletes.filter((item) => {
          const haystack = [item.name, item.studentNo, item.idNo, item.athleteType, item.category]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();
          return haystack.indexOf(keyword) >= 0;
        })
      : athletes;
    const filtered = base.map((item) => Object.assign({}, item, {
      checked: selectedIds.indexOf(item.id) >= 0
    }));
    this.setData({ filtered });
  },

  onSearchInput(event) {
    this.setData({ keyword: event.detail.value });
    this.applyFilter();
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/coach/athletes/edit/index' });
  },

  goEdit(id) {
    wx.navigateTo({ url: `/pages/coach/athletes/edit/index?id=${id}` });
  },

  onCardTap(event) {
    const id = Number(event.currentTarget.dataset.id);
    if (this.data.selectMode) {
      this.toggleSelect(id);
    } else {
      this.goEdit(id);
    }
  },

  toggleSelectMode() {
    this.setData({ selectMode: !this.data.selectMode, selectedIds: [] });
    this.applyFilter();
  },

  toggleSelect(id) {
    const selectedIds = this.data.selectedIds.slice();
    const idx = selectedIds.indexOf(id);
    if (idx >= 0) {
      selectedIds.splice(idx, 1);
    } else {
      selectedIds.push(id);
    }
    this.setData({ selectedIds });
    this.applyFilter();
  },

  async onToggleEnable(event) {
    const id = Number(event.currentTarget.dataset.id);
    const item = this.data.athletes.find((athlete) => athlete.id === id);
    if (!item || this.data.busyId) {
      return;
    }
    const nextEnabled = item.enabled === false;
    this.setData({ busyId: id });
    try {
      await coachApi.updateAthleteStatus(id, {
        banned: !!item.banned,
        schoolVerificationStatus: item.schoolVerificationStatus || '校验成功',
        enabled: nextEnabled
      });
      wx.showToast({ title: nextEnabled ? '已启用' : '已停用', icon: 'success' });
      await this.loadData();
    } catch (error) {
      this.showError(error.message || '操作失败');
    } finally {
      this.setData({ busyId: null });
    }
  },

  onDelete(event) {
    const id = Number(event.currentTarget.dataset.id);
    const item = this.data.athletes.find((athlete) => athlete.id === id);
    if (!item) {
      return;
    }
    wx.showModal({
      title: '删除运动员',
      content: `确定删除「${item.name}」吗？`,
      confirmColor: '#be123c',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          await coachApi.deleteAthlete(id);
          wx.showToast({ title: '已删除', icon: 'success' });
          this.loadData();
        } catch (error) {
          this.showError(error.message || '删除失败');
        }
      }
    });
  },

  onBatchDelete() {
    const ids = this.data.selectedIds;
    if (!ids.length) {
      wx.showToast({ title: '请先选择运动员', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '批量删除',
      content: `确定删除所选 ${ids.length} 名运动员吗？`,
      confirmColor: '#be123c',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          await coachApi.batchDeleteAthletes(ids);
          wx.showToast({ title: '已删除', icon: 'success' });
          this.setData({ selectMode: false, selectedIds: [] });
          this.loadData();
        } catch (error) {
          this.showError(error.message || '删除失败');
        }
      }
    });
  },

  showError(message) {
    wx.showModal({
      title: '提示',
      content: message || '操作失败',
      showCancel: false
    });
  }
});
