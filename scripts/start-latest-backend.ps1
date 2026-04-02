Set-Location 'D:\code\PE_TEACHER_ASSISTANT_JAVA'
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
mvn -q "-Dmaven.repo.local=.m2repo" spring-boot:run *> latest-backend.log
