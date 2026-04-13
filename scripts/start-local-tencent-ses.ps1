param(
    [Parameter(Mandatory = $true)]
    [string]$SenderAddress,
    [Parameter(Mandatory = $true)]
    [string]$SecretId,
    [Parameter(Mandatory = $true)]
    [string]$SecretKey,
    [Parameter(Mandatory = $true)]
    [long]$VerifyEmailTemplateId,
    [Parameter(Mandatory = $true)]
    [long]$ResetPasswordTemplateId,
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Port = 8080,
    [string]$Region = "ap-guangzhou",
    [string]$Endpoint = "ses.tencentcloudapi.com"
)

Set-Location 'D:\code\PE_TEACHER_ASSISTANT_JAVA'

$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'

$env:APP_MAIL_ENABLED = 'true'
$env:APP_MAIL_TRANSPORT = 'ses-api'
$env:APP_MAIL_PRODUCT_NAME = [string]::Concat(@([char]0x4F53, [char]0x80B2, [char]0x6559, [char]0x5E08, [char]0x52A9, [char]0x624B))
$env:APP_MAIL_FROM = $SenderAddress
$env:APP_MAIL_BASE_URL = $BaseUrl
$env:APP_MAIL_DISPATCH_ENABLED = 'true'
$env:APP_MAIL_DISPATCH_BATCH_SIZE = '20'
$env:APP_MAIL_DISPATCH_FIXED_DELAY_MS = '30000'
$env:APP_MAIL_MAX_RETRY_COUNT = '3'
$env:APP_MAIL_RETRY_DELAY_MINUTES = '5'

$env:APP_MAIL_SES_API_SECRET_ID = $SecretId
$env:APP_MAIL_SES_API_SECRET_KEY = $SecretKey
$env:APP_MAIL_SES_API_REGION = $Region
$env:APP_MAIL_SES_API_ENDPOINT = $Endpoint
$env:APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID = $VerifyEmailTemplateId.ToString()
$env:APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID = $ResetPasswordTemplateId.ToString()

$env:SERVER_PORT = $Port.ToString()

Write-Host "Tencent SES API enabled"
Write-Host "APP_MAIL_BASE_URL=$($env:APP_MAIL_BASE_URL)"
Write-Host "APP_MAIL_TRANSPORT=$($env:APP_MAIL_TRANSPORT)"
Write-Host "APP_MAIL_PRODUCT_NAME=$($env:APP_MAIL_PRODUCT_NAME)"
Write-Host "SERVER_PORT=$($env:SERVER_PORT)"
Write-Host "APP_MAIL_FROM=$($env:APP_MAIL_FROM)"
Write-Host "APP_MAIL_SES_API_REGION=$($env:APP_MAIL_SES_API_REGION)"
Write-Host "APP_MAIL_SES_API_ENDPOINT=$($env:APP_MAIL_SES_API_ENDPOINT)"
Write-Host "APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID=$($env:APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID)"
Write-Host "APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID=$($env:APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID)"

mvn -q "-Dmaven.repo.local=.m2repo" spring-boot:run
