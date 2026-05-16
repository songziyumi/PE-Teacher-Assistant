const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

const STATUS_OPTIONS = ['出勤', '缺勤', '请假'];

function formatDate(date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function buildStudentViewModel(student, attendanceMap) {
  const currentStatus = attendanceMap[String(student.id)] || attendanceMap[student.id] || '出勤';
  const metaLine = `${student.adminClassName || '未分班'} · ${student.studentNo || '无学号'}`;
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
    const className = student.adminClassName || '未分班';
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

Page({
  data: {
    classId: '',
    className: '',
    date: '',
    loading: true,
    saving: false,
    students: [],
    errorMessage: '',
    statusOptions: STATUS_OPTIONS
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
    this.loadAttendanceOnly();
  },

  async loadData() {
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const [pageData, attendanceMap] = await Promise.all([
        api.fetchTeacherClassStudents(this.data.classId, '', '', 0, 100),
        api.fetchTeacherAttendance(this.data.classId, this.data.date)
      ]);
      const students = attachGroupColors(
        sortStudents((pageData.content || []).map((student) => buildStudentViewModel(student, attendanceMap)))
      );
      this.setData({
        loading: false,
        students
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '加载点名数据失败'
      });
    }
  },

  async loadAttendanceOnly() {
    if (!this.data.students.length) {
      return;
    }
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const attendanceMap = await api.fetchTeacherAttendance(this.data.classId, this.data.date);
      const students = attachGroupColors(
        sortStudents(this.data.students.map((student) => buildStudentViewModel(student, attendanceMap)))
      );
      this.setData({
        loading: false,
        students
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || '加载考勤失败'
      });
    }
  },

  changeStatus(event) {
    const studentId = Number(event.currentTarget.dataset.studentId);
    const status = event.currentTarget.dataset.status;
    const students = this.data.students.map((student) => {
      if (student.id !== studentId) {
        return student;
      }
      return Object.assign({}, student, {
        currentStatus: status
      });
    });
    this.setData({ students: attachGroupColors(students) });
  },

  async saveAttendance() {
    if (this.data.saving || !this.data.students.length) {
      return;
    }
    this.setData({
      saving: true,
      errorMessage: ''
    });
    try {
      const records = this.data.students.map((student) => ({
        studentId: student.id,
        status: student.currentStatus || '出勤'
      }));
      await api.saveTeacherAttendance(this.data.classId, this.data.date, records);
      wx.showToast({
        title: '保存成功',
        icon: 'success'
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || '保存失败'
      });
    } finally {
      this.setData({
        saving: false
      });
    }
  }
});
