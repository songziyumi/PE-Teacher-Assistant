class TermGrade {
  final int? id;
  final int? studentId;
  final String? studentName;
  final String? studentNo;
  final String? academicYear;
  final String? semester;
  final double? attendanceScore;
  final double? skillScore;
  final double? theoryScore;
  final double? totalScore;
  final String? level;
  final String? remark;

  const TermGrade({
    this.id,
    this.studentId,
    this.studentName,
    this.studentNo,
    this.academicYear,
    this.semester,
    this.attendanceScore,
    this.skillScore,
    this.theoryScore,
    this.totalScore,
    this.level,
    this.remark,
  });

  factory TermGrade.fromJson(Map<String, dynamic> json) => TermGrade(
        id: json['id'],
        studentId: json['student']?['id'],
        studentName: json['student']?['name'],
        studentNo: json['student']?['studentNo'],
        academicYear: json['academicYear'],
        semester: json['semester'],
        attendanceScore: (json['attendanceScore'] as num?)?.toDouble(),
        skillScore: (json['skillScore'] as num?)?.toDouble(),
        theoryScore: (json['theoryScore'] as num?)?.toDouble(),
        totalScore: (json['totalScore'] as num?)?.toDouble(),
        level: json['level'],
        remark: json['remark'],
      );

  // 前端实时计算综合分（与后端算法一致：出勤40% + 技能40% + 理论20%，空项按比例重分配）
  static double? computeTotal(double? attendance, double? skill, double? theory) {
    double sum = 0, weight = 0;
    if (attendance != null) { sum += attendance * 40; weight += 40; }
    if (skill != null) { sum += skill * 40; weight += 40; }
    if (theory != null) { sum += theory * 20; weight += 20; }
    if (weight == 0) return null;
    return (sum / weight * 10).round() / 10;
  }

  static String? computeLevel(double? total) {
    if (total == null) return null;
    if (total >= 90) return '优秀';
    if (total >= 80) return '良好';
    if (total >= 60) return '及格';
    return '不及格';
  }
}
