const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

function formatBirthDate(value) {
  if (!value) {
    return '';
  }
  return String(value).slice(0, 10);
}

function resolvePhotoUrl(photoUrl) {
  if (!photoUrl) {
    return '';
  }
  if (String(photoUrl).startsWith('http://') || String(photoUrl).startsWith('https://')) {
    return photoUrl;
  }
  const baseUrl = api.getBaseUrl();
  return `${baseUrl}${String(photoUrl).startsWith('/') ? '' : '/'}${photoUrl}`;
}

Page({
  data: {
    loading: true,
    saving: false,
    changingPassword: false,
    uploadingPhoto: false,
    editingProfile: false,
    showPasswordForm: false,
    errorMessage: '',
    profile: null,
    stats: null,
    genderOptions: ['男', '女'],
    genderIndex: -1,
    specialty: '',
    email: '',
    bio: '',
    birthDate: '',
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  },

  onShow() {
    if (!auth.getToken()) {
      wx.reLaunch({
        url: '/pages/login/index'
      });
      return;
    }
    this.loadProfilePage();
  },

  async loadProfilePage() {
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const [profile, stats] = await Promise.all([
        api.fetchTeacherProfile(),
        api.fetchTeacherProfileStats()
      ]);
      profile.photoUrl = resolvePhotoUrl(profile.photoUrl);
      const genderIndex = this.data.genderOptions.indexOf(profile.gender || '');
      this.setData({
        loading: false,
        profile,
        stats,
        genderIndex,
        specialty: profile.specialty || '',
        email: profile.email || '',
        bio: profile.bio || '',
        birthDate: formatBirthDate(profile.birthDate)
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '加载个人主页失败'
      });
    }
  },

  startEdit() {
    const profile = this.data.profile || {};
    const genderIndex = this.data.genderOptions.indexOf(profile.gender || '');
    this.setData({
      editingProfile: true,
      genderIndex,
      specialty: profile.specialty || '',
      email: profile.email || '',
      bio: profile.bio || '',
      birthDate: formatBirthDate(profile.birthDate)
    });
  },

  cancelEdit() {
    this.setData({
      editingProfile: false
    });
  },

  onGenderChange(event) {
    this.setData({
      genderIndex: Number(event.detail.value)
    });
  },

  onBirthDateChange(event) {
    this.setData({
      birthDate: event.detail.value
    });
  },

  onSpecialtyInput(event) {
    this.setData({
      specialty: event.detail.value
    });
  },

  onEmailInput(event) {
    this.setData({
      email: event.detail.value
    });
  },

  onBioInput(event) {
    this.setData({
      bio: event.detail.value
    });
  },

  onOldPasswordInput(event) {
    this.setData({
      oldPassword: event.detail.value
    });
  },

  onNewPasswordInput(event) {
    this.setData({
      newPassword: event.detail.value
    });
  },

  onConfirmPasswordInput(event) {
    this.setData({
      confirmPassword: event.detail.value
    });
  },

  async saveProfile() {
    if (this.data.saving) {
      return;
    }
    this.setData({
      saving: true,
      errorMessage: ''
    });
    try {
      const gender = this.data.genderIndex >= 0 ? this.data.genderOptions[this.data.genderIndex] : null;
      await api.updateTeacherProfile({
        gender,
        birthDate: this.data.birthDate || null,
        specialty: this.data.specialty.trim(),
        email: this.data.email.trim(),
        bio: this.data.bio.trim()
      });
      wx.showToast({
        title: '资料已保存',
        icon: 'success'
      });
      this.setData({
        editingProfile: false
      });
      await this.loadProfilePage();
    } catch (error) {
      this.setData({
        errorMessage: error.message || '保存失败'
      });
    } finally {
      this.setData({
        saving: false
      });
    }
  },

  togglePasswordForm() {
    this.setData({
      showPasswordForm: !this.data.showPasswordForm
    });
  },

  async changePassword() {
    if (this.data.changingPassword) {
      return;
    }
    const oldPassword = this.data.oldPassword || '';
    const newPassword = this.data.newPassword || '';
    const confirmPassword = this.data.confirmPassword || '';
    if (!oldPassword || !newPassword || !confirmPassword) {
      this.setData({
        errorMessage: '请完整填写密码信息'
      });
      return;
    }
    if (newPassword !== confirmPassword) {
      this.setData({
        errorMessage: '两次输入的新密码不一致'
      });
      return;
    }
    this.setData({
      changingPassword: true,
      errorMessage: ''
    });
    try {
      await api.changeTeacherPassword(oldPassword, newPassword);
      wx.showToast({
        title: '密码修改成功',
        icon: 'success'
      });
      this.setData({
        showPasswordForm: false,
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || '密码修改失败'
      });
    } finally {
      this.setData({
        changingPassword: false
      });
    }
  },

  async choosePhoto() {
    if (this.data.uploadingPhoto) {
      return;
    }
    try {
      const chooseResult = await new Promise((resolve, reject) => {
        wx.chooseMedia({
          count: 1,
          mediaType: ['image'],
          sourceType: ['album'],
          success: resolve,
          fail: reject
        });
      });
      const filePath = chooseResult.tempFiles && chooseResult.tempFiles[0] ? chooseResult.tempFiles[0].tempFilePath : '';
      if (!filePath) {
        return;
      }
      this.setData({
        uploadingPhoto: true,
        errorMessage: ''
      });
      const data = await api.uploadTeacherProfilePhoto(filePath);
      const profile = Object.assign({}, this.data.profile || {}, {
        photoUrl: resolvePhotoUrl(data.photoUrl || '')
      });
      this.setData({
        profile
      });
      wx.showToast({
        title: '头像上传成功',
        icon: 'success'
      });
    } catch (error) {
      if (error && error.errMsg && error.errMsg.includes('cancel')) {
        return;
      }
      this.setData({
        errorMessage: error.message || '头像上传失败'
      });
    } finally {
      this.setData({
        uploadingPhoto: false
      });
    }
  }
});
