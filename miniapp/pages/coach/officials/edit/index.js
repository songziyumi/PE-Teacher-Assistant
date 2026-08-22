const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');
const { OFFICIAL_DICT } = require('../../../../utils/coach-dict.js');

const PERSON_TYPES = OFFICIAL_DICT.personType;
const ID_TYPES = OFFICIAL_DICT.idType;
const GENDERS = OFFICIAL_DICT.gender;

Page({
  data: {
    id: null,
    loading: false,
    submitting: false,
    loadError: '',
    teamId: null,
    personTypeOptions: PERSON_TYPES,
    idTypeOptions: ID_TYPES,
    genderOptions: GENDERS,
    personTypeIndex: -1,
    idTypeIndex: -1,
    genderIndex: -1,
    photoPreview: '',
    form: {
      personType: '', idType: '', idNo: '', name: '', phone: '', gender: '',
      photoUrl: '', nation: '', bloodType: '', coachLevel: '', remark: ''
    }
  },

  onLoad(options) {
    if (!coachAuth.getToken()) {
      wx.reLaunch({ url: '/pages/login/index' });
      return;
    }
    const id = options && options.id ? Number(options.id) : null;
    this.setData({ id });
    if (id) {
      this.loadDetail(id);
    } else {
      this.loadTeam();
    }
  },

  async loadTeam() {
    try {
      const user = await coachApi.fetchMe();
      this.setData({ teamId: Number(user && (user.teamScopeId || user.teamId)) || null });
    } catch (error) {
      this.setData({ loadError: error.message || '无法获取代表队信息' });
    }
  },

  reload() {
    if (this.data.id) {
      this.loadDetail(this.data.id);
    } else {
      this.loadTeam();
    }
  },

  async loadDetail(id) {
    this.setData({ loading: true, loadError: '' });
    try {
      const item = await coachApi.fetchOfficial(id);
      const form = {
        personType: item.personType || '', idType: item.idType || '', idNo: item.idNo || '',
        name: item.name || '', phone: item.phone || '', gender: item.gender || '',
        photoUrl: item.photoUrl || '', nation: item.nation || '', bloodType: item.bloodType || '',
        coachLevel: item.coachLevel || '', remark: item.remark || ''
      };
      this.setData({
        loading: false, teamId: item.teamId || null, form,
        personTypeIndex: PERSON_TYPES.indexOf(form.personType),
        idTypeIndex: ID_TYPES.indexOf(form.idType),
        genderIndex: GENDERS.indexOf(form.gender),
        photoPreview: this.resolvePhotoUrl(form.photoUrl)
      });
    } catch (error) {
      this.setData({ loading: false, loadError: error.message || '加载失败' });
    }
  },

  resolvePhotoUrl(photoUrl) {
    if (!photoUrl) return '';
    const value = String(photoUrl);
    if (/^https?:\/\//i.test(value)) return value;
    return `${coachApi.getBaseUrl()}${value.indexOf('/') === 0 ? '' : '/'}${value}`;
  },

  onFieldInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value });
  },

  onPersonTypeChange(event) {
    const index = Number(event.detail.value);
    this.setData({ personTypeIndex: index, 'form.personType': PERSON_TYPES[index] || '' });
  },

  onIdTypeChange(event) {
    const index = Number(event.detail.value);
    this.setData({ idTypeIndex: index, 'form.idType': ID_TYPES[index] || '' });
  },

  onGenderChange(event) {
    const index = Number(event.detail.value);
    this.setData({ genderIndex: index, 'form.gender': GENDERS[index] || '' });
  },

  choosePhoto() {
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const filePath = res.tempFilePaths && res.tempFilePaths[0];
        if (!filePath) return;
        wx.showLoading({ title: '上传中...', mask: true });
        try {
          const path = await coachApi.uploadPhoto(filePath);
          this.setData({ 'form.photoUrl': path, photoPreview: this.resolvePhotoUrl(path) });
        } catch (error) {
          this.showError(error.message || '上传失败');
        } finally {
          wx.hideLoading();
        }
      }
    });
  },

  async handleSubmit() {
    if (this.data.submitting) return;
    const form = this.data.form;
    const required = [
      ['personType', '请选择人员类型'], ['idType', '请选择证件类型'], ['idNo', '请填写证件号'],
      ['name', '请填写姓名'], ['phone', '请填写联系电话'], ['gender', '请选择性别'],
      ['photoUrl', '请上传官员照片'], ['nation', '请填写民族']
    ];
    const missing = required.find(([field]) => !(form[field] || '').trim());
    if (missing) return wx.showToast({ title: missing[1], icon: 'none' });
    const teamId = Number(this.data.teamId);
    if (!teamId) return this.showError('无法确定本队信息，请重新登录后重试');
    const payload = {
      teamId,
      personType: form.personType,
      idType: form.idType,
      idNo: form.idNo.trim(),
      name: form.name.trim(),
      phone: form.phone.trim(),
      gender: form.gender,
      photoUrl: form.photoUrl,
      nation: form.nation.trim(),
      bloodType: this.nullIfBlank(form.bloodType),
      coachLevel: this.nullIfBlank(form.coachLevel),
      remark: this.nullIfBlank(form.remark)
    };
    this.setData({ submitting: true });
    try {
      if (this.data.id) await coachApi.updateOfficial(this.data.id, payload);
      else await coachApi.createOfficial(payload);
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 600);
    } catch (error) {
      this.setData({ submitting: false });
      this.showError(error.message || '保存失败');
    }
  },

  nullIfBlank(value) {
    const text = (value || '').trim();
    return text || null;
  },

  showError(message) {
    wx.showModal({ title: '提示', content: message || '操作失败', showCancel: false });
  }
});
