# iOS TestFlight 发布清单（内部 10+ 人）

适用场景：Flutter 项目，内部小范围 iPhone 安装测试。

## 1. 前置条件

- 一台 macOS 设备（必须）
- 已安装 Xcode（建议最新稳定版）
- 已安装 Flutter（与项目版本匹配）
- Apple Developer 账号（建议组织账号）

## 2. Flutter 工程准备

在 `mobile/` 目录执行：

```bash
flutter doctor
flutter clean
flutter pub get
```

如果 `ios/` 目录不完整，先生成：

```bash
flutter create --platforms=ios .
```

## 3. iOS 签名配置（Xcode）

```bash
open ios/Runner.xcworkspace
```

在 Xcode 中：

- `Runner` -> `Signing & Capabilities`
- `Team`：选择你的开发者团队
- `Bundle Identifier`：全局唯一（如 `com.xxx.peassistant`）
- 勾选自动签名（Automatically manage signing）

## 4. 版本号规范

`ios/Runner.xcodeproj` -> `General`：

- `Version`：对外版本号（如 `1.0.0`）
- `Build`：每次上传必须递增（如 `1` -> `2` -> `3`）

发布新包前，至少更新 `Build`。

## 5. 打包与上传

在 Xcode 菜单：

1. `Product` -> `Archive`
2. 打开 Organizer
3. `Distribute App` -> `App Store Connect` -> `Upload`

上传完成后，到 App Store Connect 等待构建处理（通常数分钟到十几分钟）。

## 6. TestFlight 分发

在 App Store Connect：

1. 进入对应 App -> `TestFlight`
2. 选择构建版本
3. 添加测试员
   - 内部测试（同团队成员）最快
   - 外部测试（非团队成员）可能需要一次 Beta 审核
4. 发送邀请（邮箱或公开链接）

用户侧安装流程：

1. iPhone 安装 `TestFlight`
2. 接受邀请
3. 点击安装 App

## 7. 常见问题

- 无法安装到手机：
  - 检查是否通过 TestFlight 安装
  - 检查测试员是否被加入对应构建
- 上传失败：
  - 通常是签名或证书问题，重新检查 Team/Bundle ID/自动签名
- 看不到新版本：
  - 确认 Build 已递增
  - 确认构建已处理完成并分配给测试组

## 8. 建议流程（每次迭代）

1. 代码合并
2. `Build` 号 +1
3. Archive + Upload
4. TestFlight 分发
5. 收集反馈并进入下一轮
