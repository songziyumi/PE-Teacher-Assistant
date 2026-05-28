const api = require('../../../utils/api.js');
const auth = require('../../../utils/auth.js');

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
    genderOptions: ['\u7537', '\u5973'],
    genderIndex: -1,
    specialty: '',
    email: '',
    bio: '',
    birthDate: '',
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    text: {
      loading: '\u6b63\u5728\u52a0\u8f7d\u4e2a\u4eba\u4e3b\u9875...',
      teacherFallback: '\u6559\u5e08',
      avatarFallback: '\u5e08',
      schoolSeparator: ' \u00b7 ',
      emptyBio: '\u672a\u586b\u5199\u6559\u5e08\u7b80\u4ecb',
      overviewTitle: '\u6982\u89c8\u7edf\u8ba1',
      metricClass: '\u73ed\u52a1',
      metricClassLabel: '\u73ed\u7ea7\u6570',
      metricAttendance: '\u8003\u52e4',
      metricAttendanceLabel: '\u672c\u6708\u70b9\u540d\u5929\u6570',
      metricPending: '\u5ba1\u6279',
      metricPendingLabel: '\u5f85\u5ba1\u6279',
      metricProcessed: '\u5904\u7406',
      metricProcessedLabel: '\u5df2\u5904\u7406\u5ba1\u6279',
      basicTitle: '\u57fa\u672c\u8d44\u6599',
      changePhoto: '\u6362\u5934\u50cf',
      edit: '\u7f16\u8f91',
      gender: '\u6027\u522b',
      choose: '\u8bf7\u9009\u62e9',
      birthDate: '\u51fa\u751f\u65e5\u671f',
      chooseDate: '\u8bf7\u9009\u62e9\u65e5\u671f',
      specialty: '\u4e13\u9879',
      specialtyPlaceholder: '\u8bf7\u8f93\u5165\u4e13\u9879',
      email: '\u90ae\u7bb1',
      emailPlaceholder: '\u8bf7\u8f93\u5165\u90ae\u7bb1',
      bio: '\u7b80\u4ecb',
      bioPlaceholder: '\u4ecb\u7ecd\u4e00\u4e0b\u81ea\u5df1',
      cancel: '\u53d6\u6d88',
      save: '\u4fdd\u5b58',
      username: '\u8d26\u53f7',
      securityTitle: '\u8d26\u53f7\u5b89\u5168',
      collapse: '\u6536\u8d77',
      changePassword: '\u4fee\u6539\u5bc6\u7801',
      passwordPill: '\u5bc6\u7801\u4fdd\u62a4',
      passwordHint: '\u5efa\u8bae\u5b9a\u671f\u66f4\u65b0\u5bc6\u7801\uff0c\u907f\u514d\u957f\u671f\u4f7f\u7528\u56fa\u5b9a\u53e3\u4ee4\u3002',
      oldPassword: '\u65e7\u5bc6\u7801',
      oldPasswordPlaceholder: '\u8bf7\u8f93\u5165\u65e7\u5bc6\u7801',
      newPassword: '\u65b0\u5bc6\u7801',
      newPasswordPlaceholder: '\u8bf7\u8f93\u5165\u65b0\u5bc6\u7801',
      confirmPassword: '\u786e\u8ba4\u65b0\u5bc6\u7801',
      confirmPasswordPlaceholder: '\u8bf7\u518d\u6b21\u8f93\u5165\u65b0\u5bc6\u7801',
      submitChange: '\u63d0\u4ea4\u4fee\u6539',
      recentTitle: '\u6700\u8fd1\u5ba1\u6279\u52a8\u6001',
      approvalRecord: '\u5ba1\u6279\u8bb0\u5f55',
      activityFallback: '\u52a8\u6001',
      activitySeparator: ' \u00b7 ',
      profileSaved: '\u8d44\u6599\u5df2\u4fdd\u5b58',
      profileLoadFailed: '\u52a0\u8f7d\u4e2a\u4eba\u4e3b\u9875\u5931\u8d25',
      profileSaveFailed: '\u4fdd\u5b58\u5931\u8d25',
      passwordIncomplete: '\u8bf7\u5b8c\u6574\u586b\u5199\u5bc6\u7801\u4fe1\u606f',
      passwordMismatch: '\u4e24\u6b21\u8f93\u5165\u7684\u65b0\u5bc6\u7801\u4e0d\u4e00\u81f4',
      passwordChanged: '\u5bc6\u7801\u4fee\u6539\u6210\u529f',
      passwordChangeFailed: '\u5bc6\u7801\u4fee\u6539\u5931\u8d25',
      photoUploaded: '\u5934\u50cf\u4e0a\u4f20\u6210\u529f',
      photoUploadFailed: '\u5934\u50cf\u4e0a\u4f20\u5931\u8d25'
    }
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
        errorMessage: error.message || this.data.text.profileLoadFailed
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
        title: this.data.text.profileSaved,
        icon: 'success'
      });
      this.setData({
        editingProfile: false
      });
      await this.loadProfilePage();
    } catch (error) {
      this.setData({
        errorMessage: error.message || this.data.text.profileSaveFailed
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
        errorMessage: this.data.text.passwordIncomplete
      });
      return;
    }
    if (newPassword !== confirmPassword) {
      this.setData({
        errorMessage: this.data.text.passwordMismatch
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
        title: this.data.text.passwordChanged,
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
        errorMessage: error.message || this.data.text.passwordChangeFailed
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
        title: this.data.text.photoUploaded,
        icon: 'success'
      });
    } catch (error) {
      if (error && error.errMsg && error.errMsg.includes('cancel')) {
        return;
      }
      this.setData({
        errorMessage: error.message || this.data.text.photoUploadFailed
      });
    } finally {
      this.setData({
        uploadingPhoto: false
      });
    }
  }
});
