param(
    [Parameter(Mandatory = $true)]
    [string]$QqMailUser,
    [Parameter(Mandatory = $true)]
    [string]$QqMailAuthCode,
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Port = 8080
)

Set-Location 'D:\code\PE_TEACHER_ASSISTANT_JAVA'

$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'

$env:APP_MAIL_ENABLED = 'true'
$env:APP_MAIL_TRANSPORT = 'smtp'
$env:APP_MAIL_FROM = $QqMailUser
$env:APP_MAIL_BASE_URL = $BaseUrl
$env:APP_MAIL_DISPATCH_ENABLED = 'true'
$env:APP_MAIL_DISPATCH_BATCH_SIZE = '20'
$env:APP_MAIL_DISPATCH_FIXED_DELAY_MS = '30000'
$env:APP_MAIL_MAX_RETRY_COUNT = '3'
$env:APP_MAIL_RETRY_DELAY_MINUTES = '5'

$env:SPRING_MAIL_HOST = 'smtp.qq.com'
$env:SPRING_MAIL_PORT = '465'
$env:SPRING_MAIL_USERNAME = $QqMailUser
$env:SPRING_MAIL_PASSWORD = $QqMailAuthCode
$env:SPRING_MAIL_PROTOCOL = 'smtp'
$env:SPRING_MAIL_SMTP_AUTH = 'true'
$env:SPRING_MAIL_SMTP_SSL_ENABLE = 'true'
$env:SPRING_MAIL_SMTP_STARTTLS_ENABLE = 'false'
$env:SPRING_MAIL_SMTP_STARTTLS_REQUIRED = 'false'

$env:SERVER_PORT = $Port.ToString()

Write-Host "QQ SMTP enabled"
Write-Host "APP_MAIL_BASE_URL=$($env:APP_MAIL_BASE_URL)"
Write-Host "SERVER_PORT=$($env:SERVER_PORT)"
Write-Host "SPRING_MAIL_USERNAME=$($env:SPRING_MAIL_USERNAME)"

mvn -q "-Dmaven.repo.local=.m2repo" spring-boot:run
