const api = require('../../../utils/api');
const auth = require('../../../utils/auth');

const FIELD_DEFINITIONS = [
  { key: 'height', label: '\u8eab\u9ad8', unit: 'cm', zeroAsEmpty: true },
  { key: 'weight', label: '\u4f53\u91cd', unit: 'kg', zeroAsEmpty: true },
  { key: 'bmi', label: 'BMI', unit: '', zeroAsEmpty: true },
  { key: 'lungCapacity', label: '\u80ba\u6d3b\u91cf', unit: 'ml', zeroAsEmpty: true },
  { key: 'sprint50m', label: '50\u7c73\u8dd1', unit: '\u79d2', zeroAsEmpty: true },
  { key: 'sitReach', label: '\u5750\u4f4d\u4f53\u524d\u5c48', unit: 'cm', zeroAsEmpty: true },
  { key: 'standingJump', label: '\u7acb\u5b9a\u8df3\u8fdc', unit: 'cm', zeroAsEmpty: true },
  { key: 'pullUps', label: '\u5f15\u4f53\u5411\u4e0a', unit: '\u6b21', gender: 'male' },
  { key: 'sitUps', label: '\u4ef0\u5367\u8d77\u5750', unit: '\u6b21', gender: 'female' },
  { key: 'run800m', label: '800\u7c73\u8dd1', unit: '\u79d2', gender: 'female', zeroAsEmpty: true },
  { key: 'run1000m', label: '1000\u7c73\u8dd1', unit: '\u79d2', gender: 'male', zeroAsEmpty: true },
  { key: 'remark', label: '\u5907\u6ce8', unit: '', multiline: true }
];

function formatValue(value, unit, zeroAsEmpty) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (zeroAsEmpty && Number(value) === 0) {
    return '';
  }
  return unit ? `${value} ${unit}` : String(value);
}

function shouldShowField(field, record) {
  if (!field.gender) {
    return true;
  }
  const gender = record && record.student && record.student.gender ? record.student.gender : record && record.gender;
  if (field.gender === 'male') {
    return gender === '\u7537';
  }
  if (field.gender === 'female') {
    return gender === '\u5973';
  }
  return true;
}

function buildFieldItems(record) {
  return FIELD_DEFINITIONS
    .filter((field) => shouldShowField(field, record))
    .map((field) => ({
      key: field.key,
      label: field.label,
      multiline: !!field.multiline,
      value: formatValue(record[field.key], field.unit, field.zeroAsEmpty)
    }));
}

function buildBreakdownItems(items, text) {
  const fieldMap = FIELD_DEFINITIONS.reduce((accumulator, field) => {
    accumulator[field.key] = field;
    return accumulator;
  }, {});

  return (items || []).map((item) => {
    const field = fieldMap[item.key] || {};
    return {
      key: item.key,
      label: field.label || item.key,
      multiline: !!field.multiline,
      value: item.value === null || item.value === undefined || item.value === '' ? '' : formatValue(item.value, field.unit || '', field.zeroAsEmpty),
      score: item.score === null || item.score === undefined ? '' : String(item.score),
      level: item.level || '',
      levelClass: buildLevelClass(item.level)
    };
  });
}

function buildLevelClass(level) {
  if (level === '\u4f18\u79c0') return 'level-excellent';
  if (level === '\u826f\u597d') return 'level-good';
  if (level === '\u53ca\u683c') return 'level-pass';
  if (level === '\u4e0d\u53ca\u683c') return 'level-fail';
  return 'level-empty';
}

function buildDisplayRecord(record, text) {
  if (!record) {
    return null;
  }
  return Object.assign({}, record, {
    displayTotalScore: record.totalScore !== null && record.totalScore !== undefined ? record.totalScore : text.noScore,
    displayLevel: record.level || text.noLevel,
    displayBmi: record.bmi !== null && record.bmi !== undefined ? record.bmi : '-',
    displayTestDate: record.testDate || '-',
    levelClass: buildLevelClass(record.level)
  });
}

Page({
  data: {
    classId: 0,
    studentId: 0,
    studentName: '',
    className: '',
    academicYear: '',
    semester: '',
    loading: true,
    errorMessage: '',
    record: null,
    fieldItems: [],
    breakdownItems: [],
    text: {
      title: '\u4f53\u8d28\u6210\u7ee9',
      subtitle: '\u67e5\u770b\u5f53\u524d\u5b66\u5e74\u5b66\u671f\u7684\u4f53\u6d4b\u7ed3\u679c\u3002',
      loadFailed: '\u52a0\u8f7d\u5931\u8d25',
      loading: '\u6b63\u5728\u52a0\u8f7d\u4f53\u8d28\u6210\u7ee9...',
      reload: '\u91cd\u65b0\u52a0\u8f7d',
      empty: '\u5f53\u524d\u5b66\u751f\u672c\u5b66\u671f\u8fd8\u6ca1\u6709\u4f53\u8d28\u6210\u7ee9\u3002',
      totalScore: '\u603b\u5206',
      level: '\u7b49\u7ea7',
      bmi: 'BMI',
      testDate: '\u6d4b\u8bd5\u65e5\u671f',
      detail: '\u9879\u76ee\u660e\u7ec6',
      itemScore: '\u9879\u76ee\u5206',
      itemLevel: '\u9879\u76ee\u7b49\u7ea7',
      classInfo: '\u6240\u5c5e\u73ed\u7ea7',
      termInfo: '\u5b66\u5e74\u5b66\u671f',
      noLevel: '\u6682\u65e0\u7b49\u7ea7',
      noScore: '\u6682\u65e0\u603b\u5206'
    }
  },

  onLoad(options) {
    this.setData({
      classId: Number(options.classId || 0),
      studentId: Number(options.studentId || 0),
      studentName: decodeURIComponent(options.studentName || ''),
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
    this.loadDetail();
  },

  async loadDetail() {
    if (!this.data.classId || !this.data.studentId) {
      this.setData({
        loading: false,
        errorMessage: '\u7f3a\u5c11\u5b66\u751f\u53c2\u6570'
      });
      return;
    }

    this.setData({
      loading: true,
      errorMessage: ''
    });

    try {
      const result = await api.fetchTeacherPhysicalDetail(
        this.data.classId,
        this.data.studentId,
        this.data.academicYear,
        this.data.semester
      );
      const rawRecord = result.record || null;
      const record = buildDisplayRecord(rawRecord, this.data.text);
      this.setData({
        loading: false,
        record,
        fieldItems: record ? buildFieldItems(record) : [],
        breakdownItems: buildBreakdownItems(result.breakdown || [], this.data.text)
      });
    } catch (error) {
      this.setData({
        loading: false,
        errorMessage: error.message || this.data.text.loadFailed
      });
    }
  },
});
