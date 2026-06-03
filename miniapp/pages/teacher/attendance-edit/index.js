const api = require('../../../utils/api.js');
const auth = require('../../../utils/auth.js');

const PRESENT_STATUS = '\u51fa\u52e4';
const ABSENT_STATUS = '\u7f3a\u52e4';
const LEAVE_STATUS = '\u8bf7\u5047';
const STATUS_OPTIONS = [
  { value: PRESENT_STATUS, activeClass: 'status-chip-present', ghostClass: 'status-chip-present-ghost' },
  { value: ABSENT_STATUS, activeClass: 'status-chip-absent', ghostClass: 'status-chip-absent-ghost' },
  { value: LEAVE_STATUS, activeClass: 'status-chip-leave', ghostClass: 'status-chip-leave-ghost' }
];

function formatDate(date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function buildStudentViewModel(student, attendanceMap) {
  const currentStatus = attendanceMap[String(student.id)] || attendanceMap[student.id] || '\u51fa\u52e4';
  const className = student.adminClassName || '\u672a\u5206\u73ed';
  const studentNo = student.studentNo || '\u65e0\u5b66\u53f7';
  const metaLine = `${className} \u00b7 ${studentNo}`;
  return Object.assign({}, student, {
    currentStatus,
    metaLine
  });
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

function attachGroupColors(students) {
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
      groupToneClass: colorIndex === 0 ? 'student-item-tone-a' : 'student-item-tone-b'
    });
  });
}

function buildStats(students) {
  return {
    present: students.filter((student) => student.currentStatus === PRESENT_STATUS).length,
    absent: students.filter((student) => student.currentStatus === ABSENT_STATUS).length,
    leave: students.filter((student) => student.currentStatus === LEAVE_STATUS).length
  };
}

function buildVisibleStudents(students, selectedStatusFilter) {
  const filtered = selectedStatusFilter
    ? students.filter((student) => student.currentStatus === selectedStatusFilter)
    : students;
  return attachGroupColors(filtered);
}

Page({
  data: {
    classId: '',
    className: '',
    date: '',
    loading: true,
    saving: false,
    allStudents: [],
    students: [],
    errorMessage: '',
    statusOptions: STATUS_OPTIONS,
    text: {
      defaultTitle: '\u8003\u52e4\u767b\u8bb0',
      subtitle: '\u9009\u62e9\u65e5\u671f\u540e\uff0c\u9010\u4e2a\u5b66\u751f\u5207\u6362\u51fa\u52e4\u72b6\u6001\u5e76\u4fdd\u5b58\u3002',
      dateTitle: '\u767b\u8bb0\u65e5\u671f',
      loading: '\u6b63\u5728\u52a0\u8f7d\u5b66\u751f\u4e0e\u8003\u52e4\u6570\u636e...',
      empty: '\u5f53\u524d\u73ed\u7ea7\u6682\u65e0\u5b66\u751f\u3002',
      studentList: '\u5b66\u751f\u5217\u8868',
      save: '\u4fdd\u5b58\u8003\u52e4',
      loadFailed: '\u52a0\u8f7d\u70b9\u540d\u6570\u636e\u5931\u8d25',
      attendanceLoadFailed: '\u52a0\u8f7d\u8003\u52e4\u5931\u8d25',
      saveSuccess: '\u4fdd\u5b58\u6210\u529f',
      saveFailed: '\u4fdd\u5b58\u5931\u8d25',
      defaultGenderSeparator: ' \u00b7 ',
      present: '\u51fa\u52e4',
      absent: '\u7f3a\u52e4',
      leave: '\u8bf7\u5047'
    },
    selectedStatusFilter: '',
    stats: {
      present: 0,
      absent: 0,
      leave: 0
    },
    emptyMessage: '\u5f53\u524d\u73ed\u7ea7\u6682\u65e0\u5b66\u751f\u3002'
  },

  onLoad(options) {
    this.setData({
      classId: Number(options.classId || 0),
      className: decodeURIComponent(options.className || ''),
      date: formatDate(new Date())
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

  onDateChange(event) {
    this.setData({
      date: event.detail.value
    });
    this.loadData();
  },

  async loadData() {
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const [studentRows, attendanceMap] = await Promise.all([
        api.fetchAllTeacherClassStudents(this.data.classId, '', '', 200),
        api.fetchTeacherAttendance(this.data.classId, this.data.date)
      ]);
      const allStudents = sortStudents((studentRows || []).map((student) => buildStudentViewModel(student, attendanceMap)));
      this.setData({
        loading: false,
        allStudents
      });
      this.rebuildStudentViews();
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || this.data.text.loadFailed
      });
    }
  },

  rebuildStudentViews() {
    const allStudents = this.data.allStudents || [];
    this.setData({
      students: buildVisibleStudents(allStudents, this.data.selectedStatusFilter),
      stats: buildStats(allStudents),
      emptyMessage: this.data.selectedStatusFilter
        ? `\u6682\u65e0${this.data.selectedStatusFilter}\u5b66\u751f\u3002`
        : '\u5f53\u524d\u73ed\u7ea7\u6682\u65e0\u5b66\u751f\u3002'
    });
  },

  toggleStatusFilter(event) {
    const status = event.currentTarget.dataset.status || '';
    this.setData({
      selectedStatusFilter: this.data.selectedStatusFilter === status ? '' : status
    });
    this.rebuildStudentViews();
  },

  changeStatus(event) {
    const studentId = Number(event.currentTarget.dataset.studentId);
    const status = event.currentTarget.dataset.status;
    const allStudents = this.data.allStudents.map((student) => {
      if (student.id !== studentId) {
        return student;
      }
      return Object.assign({}, student, {
        currentStatus: status
      });
    });
    this.setData({ allStudents });
    this.rebuildStudentViews();
  },

  async saveAttendance() {
    if (this.data.saving || !this.data.allStudents.length) {
      return;
    }
    this.setData({
      saving: true,
      errorMessage: ''
    });
    try {
      const records = this.data.allStudents.map((student) => ({
        studentId: student.id,
        status: student.currentStatus || '\u51fa\u52e4'
      }));
      await api.saveTeacherAttendance(this.data.classId, this.data.date, records);
      wx.showToast({
        title: this.data.text.saveSuccess,
        icon: 'success'
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || this.data.text.saveFailed
      });
    } finally {
      this.setData({
        saving: false
      });
    }
  }
});
