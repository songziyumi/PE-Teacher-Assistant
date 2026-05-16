const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

const STATUS_OPTIONS = [
  '\u5168\u90e8\u5b66\u7c4d',
  '\u5728\u7c4d',
  '\u4f11\u5b66',
  '\u957f\u5047',
  '\u6bd5\u4e1a',
  '\u5728\u5916\u501f\u8bfb',
  '\u501f\u8bfb'
];
const EDIT_STATUS_OPTIONS = STATUS_OPTIONS.slice(1);
const GENDER_OPTIONS = ['\u7537', '\u5973'];

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

function buildGroupToneStudents(students) {
  let currentGroup = '';
  let colorIndex = 0;
  return students.map((student, index) => {
    const className = student.adminClassName || '\u672a\u5206\u73ed';
    if (index === 0) {
      currentGroup = className;
    } else if (className !== currentGroup) {
      currentGroup = className;
      colorIndex = colorIndex === 0 ? 1 : 0;
    }
    return Object.assign({}, student, {
      displayClassName: className,
      displayStudentNo: student.studentNo || '\u6682\u65e0\u5b66\u53f7',
      displayGender: student.gender || '\u672a\u77e5',
      displayGenderClass: student.gender === '\u7537'
        ? 'gender-boy'
        : student.gender === '\u5973'
          ? 'gender-girl'
          : 'gender-unknown',
      displayStatus: student.studentStatus || '\u672a\u8bbe\u7f6e',
      electiveClassText: student.electiveClass || '',
      groupToneClass: colorIndex === 0 ? 'student-card-tone-a' : 'student-card-tone-b'
    });
  });
}

function buildStats(students) {
  const maleCount = students.filter((item) => item.gender === '\u7537').length;
  const femaleCount = students.filter((item) => item.gender === '\u5973').length;
  return {
    totalCount: students.length,
    maleCount,
    femaleCount
  };
}

Page({
  data: {
    classId: 0,
    className: '',
    loading: true,
    errorMessage: '',
    keyword: '',
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    genderOptions: GENDER_OPTIONS,
    editStatusOptions: EDIT_STATUS_OPTIONS,
    students: [],
    totalCount: 0,
    maleCount: 0,
    femaleCount: 0,
    text: {
      defaultTitle: '\u5b66\u751f\u5217\u8868',
      subtitle: '\u6309\u624b\u673a APP \u98ce\u683c\u5c55\u793a\uff0c\u652f\u6301\u67e5\u770b\u51fa\u52e4\u548c\u7f16\u8f91\u5b66\u751f\u4fe1\u606f\u3002',
      totalCount: '\u603b\u4eba\u6570',
      maleCount: '\u7537\u751f',
      femaleCount: '\u5973\u751f',
      filters: '\u7b5b\u9009\u6761\u4ef6',
      searchPlaceholder: '\u641c\u7d22\u59d3\u540d\u6216\u5b66\u53f7',
      reset: '\u91cd\u7f6e',
      search: '\u67e5\u8be2',
      loading: '\u6b63\u5728\u52a0\u8f7d\u5b66\u751f\u5217\u8868...',
      empty: '\u5f53\u524d\u6761\u4ef6\u4e0b\u6ca1\u6709\u5b66\u751f\u6570\u636e\u3002',
      electiveClass: '\u9009\u4fee\u73ed\uff1a',
      attendanceRecord: '\u51fa\u52e4\u8bb0\u5f55',
      editStudent: '\u7f16\u8f91\u5b66\u751f',
      loadingAttendance: '\u6b63\u5728\u52a0\u8f7d\u51fa\u52e4\u8bb0\u5f55...',
      attendanceEmpty: '\u6682\u65e0\u51fa\u52e4\u8bb0\u5f55',
      attendanceTotal: '\u5171',
      attendanceTimes: '\u6b21',
      present: '\u51fa\u52e4',
      absent: '\u7f3a\u52e4',
      leave: '\u8bf7\u5047',
      rate: '\u51fa\u52e4\u7387',
      close: '\u5173\u95ed',
      editTitle: '\u7f16\u8f91\u5b66\u751f',
      namePlaceholder: '\u59d3\u540d',
      studentNoPlaceholder: '\u5b66\u53f7',
      cancel: '\u53d6\u6d88',
      save: '\u4fdd\u5b58'
    },
    attendancePopupVisible: false,
    attendancePopupLoading: false,
    attendancePopupTitle: '',
    attendanceStats: null,
    attendanceRecords: [],
    editPopupVisible: false,
    editSaving: false,
    editStudentId: 0,
    editVersion: 0,
    editName: '',
    editStudentNo: '',
    editGenderIndex: 0,
    editStatusIndex: 0
  },

  onLoad(options) {
    this.setData({
      classId: Number(options.classId || 0),
      className: decodeURIComponent(options.className || '')
    });
  },

  onShow() {
    if (!auth.getToken()) {
      wx.reLaunch({
        url: '/pages/login/index'
      });
      return;
    }
    this.loadStudents();
  },

  onKeywordInput(event) {
    this.setData({
      keyword: event.detail.value || ''
    });
  },

  onStatusChange(event) {
    this.setData({
      statusIndex: Number(event.detail.value || 0)
    });
    this.loadStudents();
  },

  submitSearch() {
    this.loadStudents();
  },

  resetFilters() {
    this.setData({
      keyword: '',
      statusIndex: 0
    });
    this.loadStudents();
  },

  noop() {},

  closeAttendancePopup() {
    this.setData({
      attendancePopupVisible: false,
      attendancePopupLoading: false,
      attendancePopupTitle: '',
      attendanceStats: null,
      attendanceRecords: []
    });
  },

  async openAttendancePopup(event) {
    const studentId = Number(event.currentTarget.dataset.studentId || 0);
    const studentName = event.currentTarget.dataset.studentName || '';
    if (!studentId) {
      return;
    }
    this.setData({
      attendancePopupVisible: true,
      attendancePopupLoading: true,
      attendancePopupTitle: `${studentName} - ${this.data.text.attendanceRecord}`,
      attendanceStats: null,
      attendanceRecords: []
    });
    try {
      const result = await api.fetchTeacherStudentAttendanceHistory(studentId, 90);
      this.setData({
        attendancePopupLoading: false,
        attendanceStats: result.stats || null,
        attendanceRecords: result.records || []
      });
    } catch (error) {
      this.setData({
        attendancePopupLoading: false
      });
      wx.showToast({
        title: error.message || '\u52a0\u8f7d\u51fa\u52e4\u8bb0\u5f55\u5931\u8d25',
        icon: 'none'
      });
    }
  },

  openEditPopup(event) {
    const studentId = Number(event.currentTarget.dataset.studentId || 0);
    const student = this.data.students.find((item) => item.id === studentId);
    if (!student) {
      return;
    }
    const genderIndex = GENDER_OPTIONS.indexOf(student.gender || '');
    const statusIndex = EDIT_STATUS_OPTIONS.indexOf(student.studentStatus || '');
    this.setData({
      editPopupVisible: true,
      editSaving: false,
      editStudentId: student.id,
      editVersion: Number(student.version || 0),
      editName: student.name || '',
      editStudentNo: student.studentNo || '',
      editGenderIndex: genderIndex >= 0 ? genderIndex : 0,
      editStatusIndex: statusIndex >= 0 ? statusIndex : 0
    });
  },

  closeEditPopup() {
    if (this.data.editSaving) {
      return;
    }
    this.setData({
      editPopupVisible: false
    });
  },

  onEditNameInput(event) {
    this.setData({
      editName: event.detail.value || ''
    });
  },

  onEditStudentNoInput(event) {
    this.setData({
      editStudentNo: event.detail.value || ''
    });
  },

  onEditGenderChange(event) {
    this.setData({
      editGenderIndex: Number(event.detail.value || 0)
    });
  },

  onEditStatusChange(event) {
    this.setData({
      editStatusIndex: Number(event.detail.value || 0)
    });
  },

  async saveEditStudent() {
    if (this.data.editSaving || !this.data.editStudentId) {
      return;
    }
    const name = (this.data.editName || '').trim();
    const studentNo = (this.data.editStudentNo || '').trim();
    const gender = this.data.genderOptions[this.data.editGenderIndex] || GENDER_OPTIONS[0];
    const studentStatus = this.data.editStatusOptions[this.data.editStatusIndex] || EDIT_STATUS_OPTIONS[0];

    if (!name) {
      wx.showToast({
        title: '\u8bf7\u8f93\u5165\u59d3\u540d',
        icon: 'none'
      });
      return;
    }
    if (!studentNo) {
      wx.showToast({
        title: '\u8bf7\u8f93\u5165\u5b66\u53f7',
        icon: 'none'
      });
      return;
    }

    this.setData({
      editSaving: true
    });

    try {
      const checkResult = await api.checkTeacherStudentNo(studentNo, this.data.editStudentId);
      if (checkResult && checkResult.available === false) {
        throw new Error(checkResult.message || '\u5b66\u53f7\u5df2\u5b58\u5728');
      }
      await api.updateTeacherStudent(this.data.editStudentId, {
        name,
        studentNo,
        gender,
        studentStatus,
        version: this.data.editVersion
      });
      this.setData({
        editPopupVisible: false,
        editSaving: false
      });
      wx.showToast({
        title: '\u4fdd\u5b58\u6210\u529f',
        icon: 'success'
      });
      await this.loadStudents();
    } catch (error) {
      this.setData({
        editSaving: false
      });
      wx.showToast({
        title: error.message || '\u7f16\u8f91\u5b66\u751f\u5931\u8d25',
        icon: 'none'
      });
    }
  },

  async loadStudents() {
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
      const statusValue =
        this.data.statusOptions[this.data.statusIndex] || '\u5168\u90e8\u5b66\u7c4d';
      const pageData = await api.fetchTeacherClassStudents(
        this.data.classId,
        (this.data.keyword || '').trim(),
        statusValue === '\u5168\u90e8\u5b66\u7c4d' ? '' : statusValue,
        0,
        200
      );
      const sortedStudents = buildGroupToneStudents(sortStudents(pageData.content || []));
      const stats = buildStats(sortedStudents);
      this.setData(Object.assign({
        loading: false,
        students: sortedStudents
      }, stats));
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '\u52a0\u8f7d\u5b66\u751f\u5217\u8868\u5931\u8d25'
      });
    }
  }
});
