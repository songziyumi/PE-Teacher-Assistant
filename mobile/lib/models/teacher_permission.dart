class TeacherPermission {
  final bool editStudentName;
  final bool editStudentGender;
  final bool editStudentNo;
  final bool editStudentStatus;
  final bool editStudentClass;
  final bool editStudentElectiveClass;
  final bool attendanceEdit;
  final bool physicalTestEdit;
  final bool termGradeEdit;
  final bool batchOperation;

  const TeacherPermission({
    this.editStudentName = true,
    this.editStudentGender = true,
    this.editStudentNo = true,
    this.editStudentStatus = true,
    this.editStudentClass = true,
    this.editStudentElectiveClass = true,
    this.attendanceEdit = true,
    this.physicalTestEdit = true,
    this.termGradeEdit = true,
    this.batchOperation = true,
  });

  factory TeacherPermission.fromJson(Map<String, dynamic> json) =>
      TeacherPermission(
        editStudentName: json['editStudentName'] != false,
        editStudentGender: json['editStudentGender'] != false,
        editStudentNo: json['editStudentNo'] != false,
        editStudentStatus: json['editStudentStatus'] != false,
        editStudentClass: json['editStudentClass'] != false,
        editStudentElectiveClass: json['editStudentElectiveClass'] != false,
        attendanceEdit: json['attendanceEdit'] != false,
        physicalTestEdit: json['physicalTestEdit'] != false,
        termGradeEdit: json['termGradeEdit'] != false,
        batchOperation: json['batchOperation'] != false,
      );

  Map<String, bool> toJson() => {
        'editStudentName': editStudentName,
        'editStudentGender': editStudentGender,
        'editStudentNo': editStudentNo,
        'editStudentStatus': editStudentStatus,
        'editStudentClass': editStudentClass,
        'editStudentElectiveClass': editStudentElectiveClass,
        'attendanceEdit': attendanceEdit,
        'physicalTestEdit': physicalTestEdit,
        'termGradeEdit': termGradeEdit,
        'batchOperation': batchOperation,
      };

  TeacherPermission copyWith({
    bool? editStudentName,
    bool? editStudentGender,
    bool? editStudentNo,
    bool? editStudentStatus,
    bool? editStudentClass,
    bool? editStudentElectiveClass,
    bool? attendanceEdit,
    bool? physicalTestEdit,
    bool? termGradeEdit,
    bool? batchOperation,
  }) =>
      TeacherPermission(
        editStudentName: editStudentName ?? this.editStudentName,
        editStudentGender: editStudentGender ?? this.editStudentGender,
        editStudentNo: editStudentNo ?? this.editStudentNo,
        editStudentStatus: editStudentStatus ?? this.editStudentStatus,
        editStudentClass: editStudentClass ?? this.editStudentClass,
        editStudentElectiveClass:
            editStudentElectiveClass ?? this.editStudentElectiveClass,
        attendanceEdit: attendanceEdit ?? this.attendanceEdit,
        physicalTestEdit: physicalTestEdit ?? this.physicalTestEdit,
        termGradeEdit: termGradeEdit ?? this.termGradeEdit,
        batchOperation: batchOperation ?? this.batchOperation,
      );

  /// 全部允许的默认值（API 调用前用作占位）
  static const defaultAll = TeacherPermission();
}
