class PhysicalTest {
  final int? id;
  final int? studentId;
  final String? studentName;
  final String? studentNo;
  final String? gender;
  final String? academicYear;
  final String? semester;
  final String? testDate;
  final double? height;
  final double? weight;
  final double? bmi;
  final int? lungCapacity;
  final double? sprint50m;
  final double? sitReach;
  final double? standingJump;
  final int? pullUps;
  final int? sitUps;
  final double? run800m;
  final double? run1000m;
  final double? totalScore;
  final String? level;
  final String? remark;

  const PhysicalTest({
    this.id,
    this.studentId,
    this.studentName,
    this.studentNo,
    this.gender,
    this.academicYear,
    this.semester,
    this.testDate,
    this.height,
    this.weight,
    this.bmi,
    this.lungCapacity,
    this.sprint50m,
    this.sitReach,
    this.standingJump,
    this.pullUps,
    this.sitUps,
    this.run800m,
    this.run1000m,
    this.totalScore,
    this.level,
    this.remark,
  });

  factory PhysicalTest.fromJson(Map<String, dynamic> json) => PhysicalTest(
        id: json['id'],
        studentId: json['student']?['id'],
        studentName: json['student']?['name'],
        studentNo: json['student']?['studentNo'],
        gender: json['student']?['gender'],
        academicYear: json['academicYear'],
        semester: json['semester'],
        testDate: json['testDate'],
        height: (json['height'] as num?)?.toDouble(),
        weight: (json['weight'] as num?)?.toDouble(),
        bmi: (json['bmi'] as num?)?.toDouble(),
        lungCapacity: json['lungCapacity'],
        sprint50m: (json['sprint50m'] as num?)?.toDouble(),
        sitReach: (json['sitReach'] as num?)?.toDouble(),
        standingJump: (json['standingJump'] as num?)?.toDouble(),
        pullUps: json['pullUps'],
        sitUps: json['sitUps'],
        run800m: (json['run800m'] as num?)?.toDouble(),
        run1000m: (json['run1000m'] as num?)?.toDouble(),
        totalScore: (json['totalScore'] as num?)?.toDouble(),
        level: json['level'],
        remark: json['remark'],
      );
}
