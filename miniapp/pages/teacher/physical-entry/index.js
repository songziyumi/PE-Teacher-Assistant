const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

const PROJECTS = [
  { key: 'height', label: '\u8eab\u9ad8(cm)', keyboard: 'digit' },
  { key: 'weight', label: '\u4f53\u91cd(kg)', keyboard: 'digit' },
  { key: 'lungCapacity', label: '\u80ba\u6d3b\u91cf(ml)', keyboard: 'number' },
  { key: 'sprint50m', label: '50\u7c73\u8dd1(\u79d2)', keyboard: 'digit' },
  { key: 'sitReach', label: '\u5750\u4f4d\u4f53\u524d\u5c48(cm)', keyboard: 'digit' },
  { key: 'standingJump', label: '\u7acb\u5b9a\u8df3\u8fdc(cm)', keyboard: 'digit' },
  { key: 'pullUps', label: '\u5f15\u4f53\u5411\u4e0a(\u6b21)', keyboard: 'number', gender: 'male' },
  { key: 'sitUps', label: '\u4ef0\u5367\u8d77\u5750(\u6b21)', keyboard: 'number', gender: 'female' },
  { key: 'run1000m', label: '1000\u7c73\u8dd1(\u79d2)', keyboard: 'digit', gender: 'male' },
  { key: 'run800m', label: '800\u7c73\u8dd1(\u79d2)', keyboard: 'digit', gender: 'female' },
  { key: 'remark', label: '\u5907\u6ce8', keyboard: 'text' }
];

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

function normalizeValue(value) {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value);
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

function isMale(student) {
  return student.gender === '\u7537';
}

function isFemale(student) {
  return student.gender === '\u5973';
}

function projectEnabledForStudent(project, student) {
  if (!project.gender) {
    return true;
  }
  if (project.gender === 'male') {
    return isMale(student);
  }
  if (project.gender === 'female') {
    return isFemale(student);
  }
  return true;
}

function buildStudentViewModel(student, existingMap, project) {
  const existing = existingMap[String(student.id)] || existingMap[student.id] || {};
  const enabled = projectEnabledForStudent(project, student);
  const projectValue = existing[project.key];

  return Object.assign({}, student, {
    displayStudentNo: student.studentNo || '\u6682\u65e0\u5b66\u53f7',
    displayClassName: student.adminClassName || '\u672a\u5206\u73ed',
    displayGender: student.gender || '\u672a\u77e5',
    inputValue: enabled ? normalizeValue(projectValue) : '',
    inputEnabled: enabled
  });
}

Page({
  data: {
    classId: 0,
    className: '',
    academicYear: '',
    semester: '',
    semesterOptions: ['\u4e0a\u5b66\u671f', '\u4e0b\u5b66\u671f'],
    testDate: '',
    loading: true,
    saving: false,
    errorMessage: '',
    projectIndex: 0,
    onlyUnfilled: false,
    projects: PROJECTS,
    rawStudents: [],
    students: [],
    existingMap: {},
    text: {
      title: '\u4f53\u8d28\u5f55\u5165',
      subtitle: '\u6309\u9879\u76ee\u9010\u9879\u5f55\u5165\uff0c\u9002\u7528\u4e8e\u884c\u653f\u73ed\u6216\u9009\u9879\u73ed\u3002',
      academicYear: '\u5b66\u5e74',
      semester: '\u5b66\u671f',
      testDate: '\u6d4b\u8bd5\u65e5\u671f',
      query: '\u67e5\u8be2',
      loadFailed: '\u52a0\u8f7d\u5931\u8d25',
      loading: '\u6b63\u5728\u52a0\u8f7d\u4f53\u8d28\u6570\u636e...',
      empty: '\u5f53\u524d\u73ed\u7ea7\u6ca1\u6709\u5b66\u751f\u3002',
      save: '\u4fdd\u5b58',
      reload: '\u91cd\u65b0\u52a0\u8f7d',
      saveSuccess: '\u4f53\u8d28\u6570\u636e\u5df2\u4fdd\u5b58',
      notApplicable: '\u4e0d\u9002\u7528',
      projectSelect: '\u9879\u76ee\u9009\u62e9',
      currentProject: '\u5f53\u524d\u9879\u76ee',
      onlyUnfilled: '\u53ea\u770b\u5f53\u524d\u9879\u76ee\u672a\u5f55\u5165',
      studentCount: '\u5f53\u524d\u663e\u793a',
      personUnit: '\u4eba',
      valuePlaceholder: '\u8bf7\u8f93\u5165',
      studentList: '\u5b66\u751f\u5217\u8868',
      classAndNo: '\u73ed\u7ea7 / \u5b66\u53f7',
      missingClassParam: '\u7f3a\u5c11\u73ed\u7ea7\u53c2\u6570'
    }
  },

  onLoad(options) {
    const now = new Date();
    this.setData({
      classId: Number(options.classId || 0),
      className: decodeURIComponent(options.className || ''),
      academicYear: buildAcademicYear(now),
      semester: buildSemester(now),
      testDate: formatDate(now)
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

  onAcademicYearInput(event) {
    this.setData({
      academicYear: event.detail.value || ''
    });
  },

  onSemesterChange(event) {
    const index = Number(event.detail.value || 0);
    this.setData({
      semester: this.data.semesterOptions[index] || this.data.semesterOptions[0]
    });
  },

  onDateChange(event) {
    this.setData({
      testDate: event.detail.value
    });
  },

  onProjectChange(event) {
    this.setData({
      projectIndex: Number(event.currentTarget.dataset.index || 0)
    });
    this.rebuildStudentView();
  },

  onOnlyUnfilledChange(event) {
    this.setData({
      onlyUnfilled: !!event.detail.value
    });
    this.rebuildStudentView();
  },

  onProjectValueInput(event) {
    const studentId = Number(event.currentTarget.dataset.studentId || 0);
    const projectKey = event.currentTarget.dataset.projectKey || '';
    const value = event.detail.value || '';
    const existingMap = Object.assign({}, this.data.existingMap);
    const studentEntry = Object.assign({}, existingMap[String(studentId)] || existingMap[studentId] || {});
    studentEntry[projectKey] = value;
    existingMap[String(studentId)] = studentEntry;
    this.setData({
      existingMap
    });
  },

  rebuildStudentView() {
    const project = this.data.projects[this.data.projectIndex] || this.data.projects[0];
    let students = sortStudents(this.data.rawStudents).map((student) =>
      buildStudentViewModel(student, this.data.existingMap, project)
    );

    if (this.data.onlyUnfilled) {
      students = students.filter((student) => student.inputEnabled && !student.inputValue);
    }

    this.setData({ students });
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
        api.fetchTeacherPhysicalTests(this.data.classId, this.data.academicYear, this.data.semester)
      ]);

      this.setData({
        loading: false,
        rawStudents: studentPage.content || [],
        students: [],
        existingMap: existingMap || {}
      });
      this.rebuildStudentView();
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || this.data.text.loadFailed
      });
    }
  },

  buildItems() {
    return this.data.rawStudents.map((student) => {
      const existing = this.data.existingMap[String(student.id)] || this.data.existingMap[student.id] || {};
      return {
        studentId: student.id,
        academicYear: this.data.academicYear,
        semester: this.data.semester,
        testDate: this.data.testDate,
        height: existing.height === '' ? null : Number(existing.height),
        weight: existing.weight === '' ? null : Number(existing.weight),
        lungCapacity: existing.lungCapacity === '' ? null : Number(existing.lungCapacity),
        sprint50m: existing.sprint50m === '' ? null : Number(existing.sprint50m),
        sitReach: existing.sitReach === '' ? null : Number(existing.sitReach),
        standingJump: existing.standingJump === '' ? null : Number(existing.standingJump),
        pullUps: existing.pullUps === '' ? null : Number(existing.pullUps),
        sitUps: existing.sitUps === '' ? null : Number(existing.sitUps),
        run800m: existing.run800m === '' ? null : Number(existing.run800m),
        run1000m: existing.run1000m === '' ? null : Number(existing.run1000m),
        remark: existing.remark || null
      };
    });
  },

  async handleQuery() {
    await this.loadData();
  },

  async saveData() {
    if (this.data.saving) {
      return;
    }

    this.setData({
      saving: true,
      errorMessage: ''
    });

    try {
      await api.saveTeacherPhysicalTests(this.buildItems());
      wx.showToast({
        title: this.data.text.saveSuccess,
        icon: 'success'
      });
      await this.loadData();
    } catch (error) {
      this.setData({
        errorMessage: error.message || this.data.text.loadFailed
      });
    } finally {
      this.setData({
        saving: false
      });
    }
  }
});
