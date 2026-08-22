const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');

Page({
  data: {
    loading: true,
    errorMessage: '',
    keyword: '',
    officials: [],
    filtered: []
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
      const officials = await coachApi.fetchOfficials();
      const list = (officials || []).map((item) => this.decorate(item));
      this.setData({ loading: false, officials: list });
      this.applyFilter();
    } catch (error) {
      this.setData({ loading: false, errorMessage: error.message || '加载失败' });
    }
  },

  decorate(item) {
    return Object.assign({}, item, {
      photoPreview: this.resolvePhotoUrl(item.photoUrl)
    });
  },

  resolvePhotoUrl(photoUrl) {
    if (!photoUrl) return '';
    const value = String(photoUrl);
    if (/^https?:\/\//i.test(value)) return value;
    return `${coachApi.getBaseUrl()}${value.indexOf('/') === 0 ? '' : '/'}${value}`;
  },

  applyFilter() {
    const keyword = (this.data.keyword || '').trim().toLowerCase();
    const officials = this.data.officials || [];
    const filtered = keyword
      ? officials.filter((item) => [item.name, item.idNo, item.phone, item.personType]
        .filter(Boolean).join(' ').toLowerCase().indexOf(keyword) >= 0)
      : officials;
    this.setData({ filtered });
  },

  onSearchInput(event) {
    this.setData({ keyword: event.detail.value });
    this.applyFilter();
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/coach/officials/edit/index' });
  },

  onCardTap(event) {
    const id = Number(event.currentTarget.dataset.id);
    if (id) wx.navigateTo({ url: `/pages/coach/officials/edit/index?id=${id}` });
  },

  previewPhoto(event) {
    const url = event.currentTarget.dataset.url;
    if (url) wx.previewImage({ current: url, urls: [url] });
  }
});
