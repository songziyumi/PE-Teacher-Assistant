const api = require('../../utils/api.js');
const auth = require('../../utils/auth.js');

function formatDate(date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function buildAcademicYear(date) {
  const year = date.getFullYear();
  return date.getMonth() + 1 >= 9 ? `${year}-${year + 1}` : `${year - 1}-${year}`;
}

function buildSemester(date) {
  return date.getMonth() + 1 >= 2 && date.getMonth() + 1 <= 7 ? '\u4e0b\u5b66\u671f' : '\u4e0a\u5b66\u671f';
}

function buildGradeOptions(classes, allLabel) {
  const gradeNames = [];
  (classes || []).forEach((item) => {
    if (item && item.gradeName && !gradeNames.includes(item.gradeName)) {
      gradeNames.push(item.gradeName);
    }
  });
  return [allLabel].concat(gradeNames);
}

function buildClassOptions(classes, gradeName, allGradeLabel, allClassLabel) {
  const filtered = (classes || []).filter((item) => gradeName === allGradeLabel || item.gradeName === gradeName);
  return [{
    id: 0,
    name: allClassLabel
  }].concat(filtered.map((item) => ({
    id: item.id,
    name: item.name
  })));
}

function parseContentDispositionFilename(header = {}) {
  const disposition = header['Content-Disposition'] || header['content-disposition'] || '';
  if (!disposition) {
    return '';
  }
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch (error) {
      return utf8Match[1];
    }
  }
  const plainMatch = disposition.match(/filename="?([^\";]+)"?/i);
  return plainMatch && plainMatch[1] ? plainMatch[1] : '';
}

function sanitizeLocalFilename(filename) {
  const cleaned = (filename || '').replace(/[\\/:*?"<>|]/g, '_').trim();
  return cleaned || 'export.xlsx';
}

Page({
  data: {
    loading: true,
    errorMessage: '',
    home: null,
    user: null,
    isStudent: false,
    isTeacher: false,
    exporting: false,
    attendanceExportVisible: false,
    termGradeExportVisible: false,
    attendanceStartDate: '',
    attendanceEndDate: '',
    attendanceGradeOptions: [],
    attendanceGradeIndex: 0,
    attendanceClassOptions: [],
    attendanceClassIndex: 0,
    attendanceStatusOptions: [],
    attendanceStatusIndex: 0,
    termGradeAcademicYear: '',
    termGradeSemester: '',
    termGradeSemesterOptions: ['\u4e0a\u5b66\u671f', '\u4e0b\u5b66\u671f'],
    termGradeClassOptions: [],
    termGradeClassIndex: 0,
    text: {
      workbench: '\u5de5\u4f5c\u53f0',
      profile: '\u4e2a\u4eba\u4e3b\u9875',
      logout: '\u9000\u51fa',
      loading: '\u6b63\u5728\u52a0\u8f7d\u9996\u9875\u6570\u636e...',
      loadFailed: '\u52a0\u8f7d\u5931\u8d25',
      reload: '\u91cd\u65b0\u52a0\u8f7d',
      studentSummary: '\u5b66\u751f\u7aef\u6458\u8981',
      currentCourseCount: '\u5f53\u524d\u8bfe\u7a0b\u6570',
      confirmedCourseCount: '\u5df2\u786e\u8ba4\u8bfe\u7a0b',
      pendingSelectionCount: '\u5f85\u5904\u7406\u5fd7\u613f',
      unreadMessageCount: '\u672a\u8bfb\u6d88\u606f',
      teacherSummary: '\u6559\u5e08\u7aef\u6458\u8981',
      classCount: '\u73ed\u7ea7\u6570',
      pendingCourseRequestCount: '\u5f85\u5ba1\u6279',
      currentEvent: '\u5f53\u524d\u6d3b\u52a8',
      eventName: '\u540d\u79f0',
      eventStatus: '\u72b6\u6001',
      myClasses: '\u6211\u7684\u73ed\u7ea7',
      exportData: '\u5bfc\u51fa\u6570\u636e',
      exportAttendance: '\u5bfc\u51fa\u8003\u52e4\u8bb0\u5f55',
      exportStudents: '\u5bfc\u51fa\u5b66\u751f\u540d\u5355',
      exportTermGrades: '\u5bfc\u51fa\u4f53\u80b2\u6210\u7ee9',
      exportApprovals: '\u5bfc\u51fa\u5ba1\u6279\u8bb0\u5f55',
      exportHint: '\u5bfc\u51fa\u81ea\u5df1\u6240\u5e26\u73ed\u7ea7\u6570\u636e',
      exportAttendanceTitle: '\u5bfc\u51fa\u8003\u52e4\u8bb0\u5f55',
      exportTermGradesTitle: '\u5bfc\u51fa\u4f53\u80b2\u6210\u7ee9',
      startDate: '\u5f00\u59cb\u65e5\u671f',
      endDate: '\u7ed3\u675f\u65e5\u671f',
      academicYear: '\u5b66\u5e74',
      semester: '\u5b66\u671f',
      gradeFilter: '\u5e74\u7ea7',
      classFilter: '\u73ed\u7ea7',
      attendanceStatus: '\u51fa\u52e4\u60c5\u51b5',
      allGrades: '\u5168\u90e8\u5e74\u7ea7',
      allClasses: '\u5168\u90e8\u73ed\u7ea7',
      allStatuses: '\u5168\u90e8\u60c5\u51b5',
      present: '\u51fa\u52e4',
      absent: '\u7f3a\u52e4',
      leave: '\u8bf7\u5047',
      cancel: '\u53d6\u6d88',
      confirmExport: '\u786e\u8ba4\u5bfc\u51fa',
      noGrade: '\u672a\u5206\u5e74\u7ea7',
      classType: '\u73ed\u7ea7',
      attendanceEntry: '\u8003\u52e4\u5f55\u5165',
      studentList: '\u5b66\u751f\u5217\u8868',
      physicalEntry: '\u4f53\u8d28\u5f55\u5165',
      gradeEntry: '\u6210\u7ee9\u5f55\u5165',
      recentMessages: '\u6700\u8fd1\u6d88\u606f',
      unnamedMessage: '\u672a\u547d\u540d\u6d88\u606f',
      system: '\u7cfb\u7edf',
      noRecentMessages: '\u6682\u65e0\u6700\u8fd1\u6d88\u606f\u3002',
      recentActivities: '\u6700\u8fd1\u64cd\u4f5c',
      todoFeature: '\u8be5\u529f\u80fd',
      developing: '\u5f00\u53d1\u4e2d'
    }
  },

  onShow() {
    if (!auth.getToken()) {
      wx.reLaunch({
        url: '/pages/login/index'
      });
      return;
    }
    this.loadHome();
  },

  async loadHome() {
    this.setData({
      loading: true,
      errorMessage: ''
    });

    try {
      const [me, home] = await Promise.all([
        api.fetchMe(),
        api.fetchHome()
      ]);
      const user = me.user || null;
      const role = user ? user.role : '';
      const now = new Date();
      const defaultStartDate = formatDate(new Date(now.getFullYear(), now.getMonth(), 1));
      const defaultEndDate = formatDate(now);
      const teacherClasses = home && home.classes ? home.classes : [];
      const attendanceGradeOptions = buildGradeOptions(teacherClasses, this.data.text.allGrades);
      const attendanceClassOptions = buildClassOptions(
        teacherClasses,
        attendanceGradeOptions[0] || this.data.text.allGrades,
        this.data.text.allGrades,
        this.data.text.allClasses
      );
      const attendanceStatusOptions = [
        this.data.text.allStatuses,
        this.data.text.present,
        this.data.text.absent,
        this.data.text.leave
      ];
      const termGradeClassOptions = [{
        id: 0,
        name: this.data.text.allClasses
      }].concat(teacherClasses.map((item) => ({
        id: item.id,
        name: item.gradeName ? `${item.gradeName} ${item.name}` : item.name
      })));
      getApp().globalData.user = user;
      getApp().globalData.home = home;
      this.setData({
        loading: false,
        home,
        user,
        isStudent: role === 'STUDENT',
        isTeacher: role !== 'STUDENT',
        attendanceStartDate: defaultStartDate,
        attendanceEndDate: defaultEndDate,
        attendanceGradeOptions,
        attendanceGradeIndex: 0,
        attendanceClassOptions,
        attendanceClassIndex: 0,
        attendanceStatusOptions,
        attendanceStatusIndex: 0,
        termGradeAcademicYear: buildAcademicYear(now),
        termGradeSemester: buildSemester(now),
        termGradeClassOptions,
        termGradeClassIndex: 0
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '\u52a0\u8f7d\u9996\u9875\u5931\u8d25'
      });
    }
  },

  async handleRefresh() {
    await this.loadHome();
  },

  goTeacherAttendance() {
    wx.navigateTo({
      url: '/pages/teacher/attendance/index'
    });
  },

  goTeacherAttendanceForClass(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className || '';
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/attendance-edit/index?classId=${classId}&className=${encodeURIComponent(className)}`
    });
  },

  goTeacherStudentList(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className || '';
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/student-list/index?classId=${classId}&className=${encodeURIComponent(className)}`
    });
  },

  goTeacherPhysicalEntry(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className || '';
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/physical-entry/index?classId=${classId}&className=${encodeURIComponent(className)}`
    });
  },

  goTeacherTermGradeEntry(event) {
    const classId = event.currentTarget.dataset.classId;
    const className = event.currentTarget.dataset.className || '';
    if (!classId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/term-grade-entry/index?classId=${classId}&className=${encodeURIComponent(className)}`
    });
  },

  goTeacherProfile() {
    wx.navigateTo({
      url: '/pages/teacher/profile/index'
    });
  },

  async exportTeacherFile(path, successTitle) {
    if (this.data.exporting) {
      return;
    }
    this.setData({ exporting: true });
    try {
      const token = auth.getToken();
      const baseUrl = api.getBaseUrl();
      const result = await new Promise((resolve, reject) => {
        wx.downloadFile({
          url: `${baseUrl}${path}`,
          header: token ? { Authorization: `Bearer ${token}` } : {},
          success: resolve,
          fail: reject
        });
      });
      if (result.statusCode !== 200 || !result.tempFilePath) {
        throw new Error('\u5bfc\u51fa\u5931\u8d25');
      }
      const responseFilename = parseContentDispositionFilename(result.header);
      const targetFilename = sanitizeLocalFilename(responseFilename);
      let filePath = result.tempFilePath;
      if (targetFilename) {
        const savedFilePath = `${wx.env.USER_DATA_PATH}/${targetFilename}`;
        try {
          await new Promise((resolve, reject) => {
            wx.getFileSystemManager().unlink({
              filePath: savedFilePath,
              success: resolve,
              fail: () => resolve()
            });
          });
          await new Promise((resolve, reject) => {
            wx.saveFile({
              tempFilePath: result.tempFilePath,
              filePath: savedFilePath,
              success: resolve,
              fail: reject
            });
          });
          filePath = savedFilePath;
        } catch (error) {
          filePath = result.tempFilePath;
        }
      }
      await new Promise((resolve, reject) => {
        wx.openDocument({
          filePath,
          showMenu: true,
          success: resolve,
          fail: reject
        });
      });
      wx.showToast({
        title: successTitle,
        icon: 'success'
      });
    } catch (error) {
      wx.showToast({
        title: error.errMsg || error.message || '\u5bfc\u51fa\u5931\u8d25',
        icon: 'none'
      });
    } finally {
      this.setData({ exporting: false });
    }
  },

  openAttendanceExport() {
    this.setData({
      attendanceExportVisible: true
    });
  },

  openTermGradeExport() {
    this.setData({
      termGradeExportVisible: true
    });
  },

  closeAttendanceExport() {
    if (this.data.exporting) {
      return;
    }
    this.setData({
      attendanceExportVisible: false
    });
  },

  closeTermGradeExport() {
    if (this.data.exporting) {
      return;
    }
    this.setData({
      termGradeExportVisible: false
    });
  },

  noop() {},

  onAttendanceStartDateChange(event) {
    this.setData({
      attendanceStartDate: event.detail.value
    });
  },

  onAttendanceEndDateChange(event) {
    this.setData({
      attendanceEndDate: event.detail.value
    });
  },

  onAttendanceGradeChange(event) {
    const index = Number(event.detail.value || 0);
    const gradeName = this.data.attendanceGradeOptions[index] || this.data.text.allGrades;
    const attendanceClassOptions = buildClassOptions(
      this.data.home && this.data.home.classes ? this.data.home.classes : [],
      gradeName,
      this.data.text.allGrades,
      this.data.text.allClasses
    );
    this.setData({
      attendanceGradeIndex: index,
      attendanceClassOptions,
      attendanceClassIndex: 0
    });
  },

  onAttendanceClassChange(event) {
    this.setData({
      attendanceClassIndex: Number(event.detail.value || 0)
    });
  },

  onAttendanceStatusChange(event) {
    this.setData({
      attendanceStatusIndex: Number(event.detail.value || 0)
    });
  },

  onTermGradeAcademicYearInput(event) {
    this.setData({
      termGradeAcademicYear: event.detail.value || ''
    });
  },

  onTermGradeSemesterChange(event) {
    const index = Number(event.detail.value || 0);
    this.setData({
      termGradeSemester: this.data.termGradeSemesterOptions[index] || this.data.termGradeSemesterOptions[0]
    });
  },

  onTermGradeClassChange(event) {
    this.setData({
      termGradeClassIndex: Number(event.detail.value || 0)
    });
  },

  exportAttendanceRecords() {
    const startDate = this.data.attendanceStartDate;
    const endDate = this.data.attendanceEndDate;
    const gradeName = this.data.attendanceGradeOptions[this.data.attendanceGradeIndex] || this.data.text.allGrades;
    const selectedClass = this.data.attendanceClassOptions[this.data.attendanceClassIndex] || { id: 0 };
    const status = this.data.attendanceStatusOptions[this.data.attendanceStatusIndex] || this.data.text.allStatuses;
    if (!startDate || !endDate) {
      wx.showToast({
        title: '\u8bf7\u9009\u62e9\u65e5\u671f',
        icon: 'none'
      });
      return;
    }
    const params = [
      `startDate=${encodeURIComponent(startDate)}`,
      `endDate=${encodeURIComponent(endDate)}`
    ];
    if (selectedClass.id) {
      params.push(`classId=${selectedClass.id}`);
    } else if (gradeName && gradeName !== this.data.text.allGrades) {
      params.push(`gradeName=${encodeURIComponent(gradeName)}`);
    }
    if (status && status !== this.data.text.allStatuses) {
      params.push(`status=${encodeURIComponent(status)}`);
    }
    this.exportTeacherFile(
      `/api/teacher/attendance/export?${params.join('&')}`,
      '\u8003\u52e4\u5bfc\u51fa\u6210\u529f'
    );
    this.setData({
      attendanceExportVisible: false
    });
  },

  exportStudentRoster() {
    this.exportTeacherFile(
      '/api/teacher/students/export',
      '\u540d\u5355\u5bfc\u51fa\u6210\u529f'
    );
  },

  exportTeacherTermGrades() {
    const academicYear = this.data.termGradeAcademicYear;
    const semester = this.data.termGradeSemester;
    const selectedClass = this.data.termGradeClassOptions[this.data.termGradeClassIndex] || { id: 0 };
    if (!academicYear || !semester) {
      wx.showToast({
        title: '\u8bf7\u9009\u62e9\u5b66\u5e74\u5b66\u671f',
        icon: 'none'
      });
      return;
    }
    const params = [
      `academicYear=${encodeURIComponent(academicYear)}`,
      `semester=${encodeURIComponent(semester)}`
    ];
    if (selectedClass.id) {
      params.push(`classId=${selectedClass.id}`);
    }
    this.exportTeacherFile(
      `/api/teacher/term-grades/export?${params.join('&')}`,
      '\u4f53\u80b2\u6210\u7ee9\u5bfc\u51fa\u6210\u529f'
    );
    this.setData({
      termGradeExportVisible: false
    });
  },

  exportApprovalRecords() {
    this.exportTeacherFile(
      '/api/teacher/course-requests/export',
      '\u5ba1\u6279\u8bb0\u5f55\u5bfc\u51fa\u6210\u529f'
    );
  },

  showTodoFeature(event) {
    const label = event.currentTarget.dataset.label || this.data.text.todoFeature;
    wx.showToast({
      title: `${label}${this.data.text.developing}`,
      icon: 'none'
    });
  },

  handleLogout() {
    auth.clearAll();
    getApp().globalData.user = null;
    getApp().globalData.home = null;
    wx.reLaunch({
      url: '/pages/login/index'
    });
  }
});
