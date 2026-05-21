const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

function sortStudents(students) {
  return [...students].sort((left, right) => {
    const leftNo = left.studentNo || '';
    const rightNo = right.studentNo || '';
    const noCompare = leftNo.localeCompare(rightNo, 'en');
    if (noCompare !== 0) {
      return noCompare;
    }
    return (left.name || '').localeCompare(right.name || '', 'zh-Hans-CN');
  });
}

function buildLevelClass(level) {
  if (level === '\u4f18\u79c0') return 'level-excellent';
  if (level === '\u826f\u597d') return 'level-good';
  if (level === '\u53ca\u683c') return 'level-pass';
  if (level === '\u4e0d\u53ca\u683c') return 'level-fail';
  return 'level-empty';
}

function buildStudents(students, recordMap, text) {
  return sortStudents(students).map((student) => {
    const record = recordMap[String(student.id)] || recordMap[student.id] || null;
    return Object.assign({}, student, {
      displayStudentNo: student.studentNo || text.noStudentNo,
      displayGender: student.gender || text.unknownGender,
      displayTotalScore: record && record.totalScore !== null && record.totalScore !== undefined
        ? String(record.totalScore)
        : text.emptyScore,
      displayLevel: record && record.level ? record.level : text.emptyLevel,
      levelClass: buildLevelClass(record && record.level ? record.level : ''),
      hasRecord: !!record
    });
  });
}

Page({
  data: {
    classId: 0,
    className: '',
    academicYear: '',
    semester: '',
    loading: true,
    errorMessage: '',
    students: [],
    text: {
      title: '\u4f53\u8d28\u603b\u89c8',
      subtitle: '\u67e5\u770b\u5168\u73ed\u5b66\u751f\u4f53\u8d28\u5065\u5eb7\u603b\u5206\u548c\u7b49\u7ea7\u3002',
      loading: '\u6b63\u5728\u52a0\u8f7d\u4f53\u8d28\u603b\u89c8...',
      loadFailed: '\u52a0\u8f7d\u5931\u8d25',
      reload: '\u91cd\u65b0\u52a0\u8f7d',
      empty: '\u5f53\u524d\u73ed\u7ea7\u6682\u65e0\u4f53\u8d28\u6210\u7ee9\u6570\u636e\u3002',
      totalScore: '\u603b\u5206',
      level: '\u7b49\u7ea7',
      noStudentNo: '\u6682\u65e0\u5b66\u53f7',
      unknownGender: '\u672a\u77e5',
      emptyScore: '',
      emptyLevel: '',
      count: '\u5168\u73ed'
    }
  },

  onLoad(options) {
    this.setData({
      classId: Number(options.classId || 0),
      className: decodeURIComponent(options.className || ''),
      academicYear: decodeURIComponent(options.academicYear || ''),
      semester: decodeURIComponent(options.semester || '')
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

  async loadData() {
    if (!this.data.classId) {
      this.setData({
        loading: false,
        errorMessage: '\u7f3a\u5c11\u73ed\u7ea7\u53c2\u6570'
      });
      return;
    }

    this.setData({
      loading: true,
      errorMessage: ''
    });

    try {
      const [studentPage, recordMap] = await Promise.all([
        api.fetchTeacherClassStudents(this.data.classId, '', '', 0, 200),
        api.fetchTeacherPhysicalTests(this.data.classId, this.data.academicYear, this.data.semester)
      ]);
      const students = buildStudents(studentPage.content || [], recordMap || {}, this.data.text);
      this.setData({
        loading: false,
        students
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || this.data.text.loadFailed
      });
    }
  },

  goDetail(event) {
    const studentId = Number(event.currentTarget.dataset.studentId || 0);
    const studentName = event.currentTarget.dataset.studentName || '';
    if (!studentId) {
      return;
    }
    wx.navigateTo({
      url: `/pages/teacher/physical-detail/index?classId=${this.data.classId}&studentId=${studentId}&studentName=${encodeURIComponent(studentName)}&className=${encodeURIComponent(this.data.className || '')}&academicYear=${encodeURIComponent(this.data.academicYear)}&semester=${encodeURIComponent(this.data.semester)}`
    });
  }
});
