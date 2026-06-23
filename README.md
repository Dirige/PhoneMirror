# PhoneMirror - 手机镜像

车机端通过局域网远程控制手机的投屏方案。

## 功能特性

- 手机端屏幕采集 - H.264 硬编码推流，低延迟
- 车机端悬浮窗显示 - 自由缩放窗口，等比缩放
- 触控事件回传 - 基于无障碍服务，车机直接操控手机
- 局域网自动发现 - UDP 广播，一键扫描设备
- Material Design UI - 深色主题，现代设计语言
- 低延迟传输 - 优化的 TCP 二进制协议

## 下载

前往 Releases 页面下载最新 APK。

- PhoneMirror-Server.apk - 手机端（发射端）
- PhoneMirror-Client.apk - 车机端（接收端）

## 快速开始

### 1. 手机端（发射端）

1. 安装 PhoneMirror-Server.apk
2. 打开 app，点击「开始投屏」
3. 授权屏幕采集权限
4. 记录显示的 IP 地址

### 2. 车机端（接收端）

1. 安装 PhoneMirror-Client.apk
2. 打开 app，输入手机 IP 地址
3. 点击「连接投屏」
4. 授权悬浮窗权限

### 3. 触控回传（可选）

在手机端设置中开启「手机镜像-发射端」的无障碍服务。

## 项目结构

PhoneMirror/
  common/          # 共享协议定义
  server/          # 手机端（发射端）
  client/          # 车机端（接收端）
  .github/         # CI/CD 配置

## 编译

GitHub Actions（推荐）

Push 到 main 分支自动编译，APK 在 Releases 页面。

本地编译：

  ./gradlew :server:assembleDebug :client:assembleDebug

## 技术栈

- 语言: Kotlin 1.9
- UI: Material Design Components
- 视频: MediaCodec (H.264)
- 网络: TCP Socket + UDP Discovery
- 最低版本: Android 5.0 (API 21)
- 目标版本: Android 14 (API 34)

## License

MIT License
