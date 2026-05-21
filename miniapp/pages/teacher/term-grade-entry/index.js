const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

function buildAcademicYear(date) {
  const year = date.getFullYear();
  return date.getMonth() + 1 >= 9 ? `${year}-${year + 1}` : `${year - 1}-${year}`;
}

function buildSemester(date) {
  return date.getMonth() + 1 >= 2 && date.getMonth() + 1 <= 7 ? '\u4e0b\u5b66\u671f' : '\u4e0a\u5b66\u671f';
}

function normalizeValue(value) {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value);
}

function parseOptionalNumber(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function setBeforeUnloadAlert(enabled, message) {
  if (enabled) {
    if (typeof wx.enableAlertBeforeUnload === 'function') {
      wx.enableAlertBeforeUnload({
        message
      });
    }
    return;
  }

  if (typeof wx.disableAlertBeforeUnload === 'function') {
    wx.disableAlertBeforeUnload();
  }
}

function sortStudents(students) {
  return [...students].sort((left, right) => {
    const leftClass = left.adminClassName || '';
    const rightClass = right.adminClassName || '';
    const classCompare = leftClass.localeCompare(rightClass, 'zh-Hans-CN');
    if (classCompare !== 0) {
      return classCompare;
    }
    const leftNo = left.studentNo || '';
    const rightNo = right.studentNo || '';
    const noCompare = leftNo.localeCompare(rightNo, 'en');
    if (noCompare !== 0) {
      return noCompare;
    }
    return (left.name || '').localeCompare(right.name || '', 'zh-Hans-CN');
  });
}

function buildStudentViewModel(student, existingMap) {
  const record = existingMap[String(student.id)] || existingMap[student.id] || {};
  return Object.assign({}, student, {
    displayGender: student.gender || '\u672a\u77e5',
    displayStudentNo: student.studentNo || '\u6682\u65e0\u5b66\u53f7',
    attendanceScoreText: record.attendanceScore === null || record.attendanceScore === undefined
      ? '--'
      : `${record.attendanceScore}`,
    absenceCountText: record.absenceCount === null || record.absenceCount === undefined
      ? '0'
      : `${record.absenceCount}`,
    skillScoreInput: normalizeValue(record.skillScore),
    theoryScoreInput: normalizeValue(record.theoryScore),
    remarkInput: normalizeValue(record.remark),
    totalScoreText: record.totalScore === null || record.totalScore === undefined
      ? '--'
      : `${record.totalScore}`,
    levelText: record.level || '',
    failByAbsence: Number(record.absenceCount || 0) >= 5
  });
}

Page({
  data: {
    classId: 0,
    className: '',
    academicYear: '',
    semester: '',
    semesterOptions: ['\u4e0a\u5b66\u671f', '\u4e0b\u5b66\u671f'],
    loading: true,
    saving: false,
    hasUnsavedChanges: false,
    errorMessage: '',
    students: [],
    existingMap: {},
    text: {
      title: '\u4f53\u80b2\u6210\u7ee9\u5f55\u5165',
      subtitle: '\u51fa\u52e4\u5206\u81ea\u52a8\u56de\u586b\uff1a\u7f3a\u52e4 1 \u6b21\u6263 10 \u5206\uff0c\u7f3a\u52e4 5 \u6b21\u53ca\u4ee5\u4e0a\u603b\u5206\u4e0d\u53ca\u683c\u3002',
      academicYear: '\u5b66\u5e74',
      semester: '\u5b66\u671f',
      query: '\u67e5\u8be2',
      save: '\u4fdd\u5b58',
      loading: '\u6b63\u5728\u52a0\u8f7d\u6210\u7ee9\u6570\u636e...',
      loadFailed: '\u52a0\u8f7d\u5931\u8d25',
      reload: '\u91cd\u65b0\u52a0\u8f7d',
      empty: '\u5f53\u524d\u73ed\u7ea7\u6ca1\u6709\u5b66\u751f\u3002',
      studentList: '\u5b66\u751f\u5217\u8868',
      attendanceScore: '\u51fa\u52e4\u5206',
      absenceCount: '\u7f3a\u52e4',
      skillScore: '\u6280\u80fd\u5206',
      theoryScore: '\u7406\u8bba\u5206',
      totalScore: '\u603b\u5206',
      remark: '\u5907\u6ce8',
      scorePlaceholder: '\u8bf7\u8f93\u5165',
      autoFilled: '\u81ea\u52a8',
      saveSuccess: '\u4f53\u80b2\u6210\u7ee9\u5df2\u4fdd\u5b58',
      missingClassParam: '\u7f3a\u5c11\u73ed\u7ea7\u53c2\u6570',
      absenceRule: '\u7f3a\u52e4 5 \u6b21\u53ca\u4ee5\u4e0a',
      failMark: '\u603b\u5206\u4e0d\u53ca\u683c',
      bottomSave: '\u4fdd\u5b58',
      unsavedLeaveMessage: '\u5df2\u8f93\u5165\u6210\u7ee9\u4f46\u5c1a\u672a\u4fdd\u5b58\uff0c\u786e\u5b9a\u79bb\u5f00\u5417\uff1f'
    }
  },

  onLoad(options) {
    const now = new Date();
    this.setData({
      classId: Number(options.classId || 0),
      className: decodeURIComponent(options.className || ''),
      academicYear: buildAcademicYear(now),
      semester: buildSemester(now)
    });
  },

  onShow() {
    if (!auth.getToken()) {
      wx.reLaunch({
        url: '/pages/login/index'
      });
      return;
    }
    this.loadData();
  },

  onUnload() {
    this.disableLeaveAlert();
  },

  setDirtyState(hasUnsavedChanges) {
    this.setData({
      hasUnsavedChanges
    });
    if (hasUnsavedChanges) {
      this.enableLeaveAlert();
      return;
    }
    this.disableLeaveAlert();
  },

  enableLeaveAlert() {
    setBeforeUnloadAlert(true, this.data.text.unsavedLeaveMessage);
  },

  disableLeaveAlert() {
    setBeforeUnloadAlert(false);
  },

  onAcademicYearInput(event) {
    this.setData({
      academicYear: event.detail.value || ''
    });
    this.setDirtyState(true);
  },

  onSemesterChange(event) {
    const index = Number(event.detail.value || 0);
    this.setData({
      semester: this.data.semesterOptions[index] || this.data.semesterOptions[0]
    });
    this.setDirtyState(true);
  },

  onSkillScoreInput(event) {
    this.updateStudentRecord(event.currentTarget.dataset.studentId, {
      skillScore: parseOptionalNumber(event.detail.value),
      skillScoreInput: event.detail.value || ''
    });
  },

  onTheoryScoreInput(event) {
    this.updateStudentRecord(event.currentTarget.dataset.studentId, {
      theoryScore: parseOptionalNumber(event.detail.value),
      theoryScoreInput: event.detail.value || ''
    });
  },

  onRemarkInput(event) {
    this.updateStudentRecord(event.currentTarget.dataset.studentId, {
      remark: event.detail.value || '',
      remarkInput: event.detail.value || ''
    });
  },

  updateStudentRecord(studentId, patch) {
    const existingMap = Object.assign({}, this.data.existingMap);
    const key = String(studentId);
    const current = Object.assign({}, existingMap[key] || {});
    existingMap[key] = Object.assign(current, patch);
    this.setData({ existingMap });
    this.rebuildStudents();
    this.setDirtyState(true);
  },

  rebuildStudents() {
    this.setData({
      students: this.data.students.map((student) => buildStudentViewModel(student, this.data.existingMap))
    });
  },

  async loadData() {
    if (!this.data.classId) {
      this.setData({
        loading: false,
        errorMessage: this.data.text.missingClassParam
      });
      return;
    }
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const [studentPage, existingMap] = await Promise.all([
        api.fetchTeacherClassStudents(this.data.classId, '', '', 0, 200),
        api.fetchTeacherTermGrades(this.data.classId, this.data.academicYear, this.data.semester)
      ]);
      const students = sortStudents(studentPage && studentPage.content ? studentPage.content : []);
      this.setData({
        loading: false,
        students: students.map((student) => buildStudentViewModel(student, existingMap || {})),
        existingMap: existingMap || {}
      });
      this.setDirtyState(false);
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '\u52a0\u8f7d\u5931\u8d25'
      });
    }
  },

  handleQuery() {
    this.loadData();
  },

  async saveData() {
    if (this.data.saving || !this.data.classId) {
      return;
    }
    const items = this.data.students.map((student) => {
      const record = this.data.existingMap[String(student.id)] || this.data.existingMap[student.id] || {};
      return {
        studentId: student.id,
        skillScore: parseOptionalNumber(record.skillScoreInput !== undefined ? record.skillScoreInput : record.skillScore),
        theoryScore: parseOptionalNumber(record.theoryScoreInput !== undefined ? record.theoryScoreInput : record.theoryScore),
        remark: record.remarkInput !== undefined ? record.remarkInput : (record.remark || '')
      };
    });
    this.setData({ saving: true });
    try {
      await api.saveTeacherTermGrades(this.data.classId, this.data.academicYear, this.data.semester, items);
      this.setDirtyState(false);
      wx.showToast({
        title: this.data.text.saveSuccess,
        icon: 'success'
      });
      await this.loadData();
    } catch (error) {
      wx.showToast({
        title: error.message || '\u4fdd\u5b58\u5931\u8d25',
        icon: 'none'
      });
    } finally {
      this.setData({ saving: false });
    }
  }
});
