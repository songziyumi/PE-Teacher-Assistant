const coachApi = require('../../../../utils/coach-api.js');
const coachAuth = require('../../../../utils/coach-auth.js');
const { ATHLETE_DICT } = require('../../../../utils/coach-dict.js');

const CATEGORY = ATHLETE_DICT.category;
const ATHLETE_TYPE = ATHLETE_DICT.athleteType;
const ID_TYPE = ATHLETE_DICT.idType;
const GENDER = ATHLETE_DICT.gender;
const EMERGENCY = ATHLETE_DICT.emergencyContactType;
const PROJECTS = ATHLETE_DICT.projects;

Page({
  data: {
    id: null,
    loading: false,
    submitting: false,
    loadError: '',

    categoryOptions: CATEGORY,
    athleteTypeOptions: ATHLETE_TYPE,
    idTypeOptions: ID_TYPE,
    genderOptions: GENDER,
    emergencyOptions: EMERGENCY,
    projectItems: PROJECTS.map((name) => ({ name, checked: false })),
    projectCount: 0,

    form: {
      category: '',
      athleteType: '',
      name: '',
      studentNo: '',
      idType: '',
      idNo: '',
      gender: '',
      birthDate: '',
      bloodType: '',
      emergencyContactType: '',
      emergencyContactName: '',
      emergencyContactPhone: '',
      photoUrl: '',
      athleteIdPhotoUrl: ''
    },
    categoryIndex: -1,
    athleteTypeIndex: -1,
    idTypeIndex: -1,
    genderIndex: -1,
    emergencyIndex: -1,
    photoPreview: '',
    athleteIdPhotoPreview: ''
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
    }
  },

  reload() {
    if (this.data.id) {
      this.loadDetail(this.data.id);
    }
  },

  async loadDetail(id) {
    this.setData({ loading: true, loadError: '' });
    try {
      const item = await coachApi.fetchAthlete(id);
      const form = {
        category: item.category || '',
        athleteType: item.athleteType || '',
        name: item.name || '',
        studentNo: item.studentNo || '',
        idType: item.idType || '',
        idNo: item.idNo || '',
        gender: item.gender || '',
        birthDate: item.birthDate || '',
        bloodType: item.bloodType || '',
        emergencyContactType: item.emergencyContactType || '',
        emergencyContactName: item.emergencyContactName || '',
        emergencyContactPhone: item.emergencyContactPhone || '',
        photoUrl: item.photoUrl || '',
        athleteIdPhotoUrl: item.athleteIdPhotoUrl || ''
      };
      const selectedProjects = item.projects || [];
      this.setData({
        loading: false,
        form,
        projectItems: PROJECTS.map((name) => ({ name, checked: selectedProjects.indexOf(name) >= 0 })),
        projectCount: selectedProjects.length,
        categoryIndex: CATEGORY.indexOf(form.category),
        athleteTypeIndex: ATHLETE_TYPE.indexOf(form.athleteType),
        idTypeIndex: ID_TYPE.indexOf(form.idType),
        genderIndex: GENDER.indexOf(form.gender),
        emergencyIndex: EMERGENCY.indexOf(form.emergencyContactType),
        photoPreview: form.photoUrl ? `${coachApi.getBaseUrl()}${form.photoUrl}` : '',
        athleteIdPhotoPreview: form.athleteIdPhotoUrl ? `${coachApi.getBaseUrl()}${form.athleteIdPhotoUrl}` : ''
      });
    } catch (error) {
      this.setData({ loading: false, loadError: error.message || '加载失败' });
    }
  },

  onFieldInput(event) {
    const field = event.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: event.detail.value });
  },

  onCategoryChange(event) {
    const index = Number(event.detail.value);
    this.setData({ categoryIndex: index, 'form.category': CATEGORY[index] || '' });
  },

  onAthleteTypeChange(event) {
    const index = Number(event.detail.value);
    this.setData({ athleteTypeIndex: index, 'form.athleteType': ATHLETE_TYPE[index] || '' });
  },

  onIdTypeChange(event) {
    const index = Number(event.detail.value);
    this.setData({ idTypeIndex: index, 'form.idType': ID_TYPE[index] || '' });
  },

  onGenderChange(event) {
    const index = Number(event.detail.value);
    this.setData({ genderIndex: index, 'form.gender': GENDER[index] || '' });
  },

  onEmergencyChange(event) {
    const index = Number(event.detail.value);
    this.setData({ emergencyIndex: index, 'form.emergencyContactType': EMERGENCY[index] || '' });
  },

  onBirthDateChange(event) {
    this.setData({ 'form.birthDate': event.detail.value });
  },

  onToggleProject(event) {
    const name = event.currentTarget.dataset.name;
    const projectItems = this.data.projectItems;
    const idx = projectItems.findIndex((item) => item.name === name);
    if (idx < 0) {
      return;
    }
    const nextChecked = !projectItems[idx].checked;
    this.setData({
      [`projectItems[${idx}].checked`]: nextChecked,
      projectCount: this.data.projectCount + (nextChecked ? 1 : -1)
    });
  },

  choosePhoto(event) {
    const field = event.currentTarget.dataset.field;
    const previewField = field === 'photoUrl' ? 'photoPreview' : 'athleteIdPhotoPreview';
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const filePath = res.tempFilePaths && res.tempFilePaths[0];
        if (!filePath) {
          return;
        }
        wx.showLoading({ title: '上传中...', mask: true });
        try {
          const path = await coachApi.uploadPhoto(filePath);
          this.setData({
            [`form.${field}`]: path,
            [previewField]: `${coachApi.getBaseUrl()}${path}`
          });
          wx.hideLoading();
        } catch (error) {
          wx.hideLoading();
          this.showError(error.message || '上传失败');
        }
      }
    });
  },

  async handleSubmit() {
    if (this.data.submitting) {
      return;
    }
    const form = this.data.form;
    if (!(form.name || '').trim()) {
      return wx.showToast({ title: '请填写姓名', icon: 'none' });
    }
    if (!form.category) {
      return wx.showToast({ title: '请选择类别', icon: 'none' });
    }
    if (!form.athleteType) {
      return wx.showToast({ title: '请选择运动员类型', icon: 'none' });
    }
    if (!form.gender) {
      return wx.showToast({ title: '请选择性别', icon: 'none' });
    }
    if (!this.data.projectCount) {
      return wx.showToast({ title: '请至少选择 1 个注册项目', icon: 'none' });
    }
    const ecType = (form.emergencyContactType || '').trim();
    const ecName = (form.emergencyContactName || '').trim();
    const ecPhone = (form.emergencyContactPhone || '').trim();
    if ((ecType || ecName || ecPhone) && !(ecType && ecName && ecPhone)) {
      return this.showError('紧急联系人信息需完整填写类型、姓名和联系电话');
    }

    const payload = {
      category: form.category,
      athleteType: form.athleteType,
      name: form.name.trim(),
      studentNo: this.nullIfBlank(form.studentNo),
      idType: this.nullIfBlank(form.idType),
      idNo: this.nullIfBlank(form.idNo),
      gender: form.gender,
      birthDate: this.nullIfBlank(form.birthDate),
      bloodType: this.nullIfBlank(form.bloodType),
      emergencyContactType: this.nullIfBlank(form.emergencyContactType),
      emergencyContactName: this.nullIfBlank(form.emergencyContactName),
      emergencyContactPhone: this.nullIfBlank(form.emergencyContactPhone),
      athleteIdPhotoUrl: this.nullIfBlank(form.athleteIdPhotoUrl),
      photoUrl: this.nullIfBlank(form.photoUrl),
      projects: this.data.projectItems.filter((item) => item.checked).map((item) => item.name)
    };

    this.setData({ submitting: true });
    try {
      if (this.data.id) {
        await coachApi.updateAthlete(this.data.id, payload);
      } else {
        await coachApi.createAthlete(payload);
      }
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 600);
    } catch (error) {
      this.setData({ submitting: false });
      this.showError(error.message || '保存失败');
    }
  },

  nullIfBlank(value) {
    if (value == null) {
      return null;
    }
    const text = String(value).trim();
    return text === '' ? null : text;
  },

  showError(message) {
    wx.showModal({
      title: '提示',
      content: message || '操作失败',
      showCancel: false
    });
  }
});
