const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

const PRESENT_STATUS = '出勤';
const ABSENT_STATUS = '缺勤';
const LEAVE_STATUS = '请假';
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
  const currentStatus = attendanceMap[String(student.id)] || attendanceMap[student.id] || PRESENT_STATUS;
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

async function fetchAllClassStudents(classId) {
  const allStudents = [];
  const pageSize = 200;
  let page = 0;

  while (true) {
    const pageData = await api.fetchTeacherClassStudents(classId, '', '', page, pageSize);
    const content = pageData && Array.isArray(pageData.content) ? pageData.content : [];
    allStudents.push(...content);
    if (!content.length || pageData.last === true || (typeof pageData.totalPages === 'number' && page >= pageData.totalPages - 1)) {
      break;
    }
    page += 1;
  }

  return allStudents;
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
    selectedStatusFilter: '',
    stats: {
      present: 0,
      absent: 0,
      leave: 0
    },
    emptyMessage: '当前班级暂无学生。'
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
        fetchAllClassStudents(this.data.classId),
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
        errorMessage: error.message || '加载点名数据失败'
      });
    }
  },

  rebuildStudentViews() {
    const allStudents = this.data.allStudents || [];
    this.setData({
      students: buildVisibleStudents(allStudents, this.data.selectedStatusFilter),
      stats: buildStats(allStudents),
      emptyMessage: this.data.selectedStatusFilter ? `暂无${this.data.selectedStatusFilter}学生。` : '当前班级暂无学生。'
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
        status: student.currentStatus || PRESENT_STATUS
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
