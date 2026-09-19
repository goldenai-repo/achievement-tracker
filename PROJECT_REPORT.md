# Achievement Tracker Android 项目报告

## 1. 项目概述

Achievement Tracker 是一款基于 Android 的旅行与地理成就记录应用。用户可以搜索国家、省州或其他一级行政区，在地图上查看已完成地点，记录新的 achievement，并通过账号同步和排行榜查看个人进度。

## 2. 主要功能

- Email/Password 注册、登录和退出
- Google 登录及 Google 账号关联
- 国家、省州和一级行政区搜索
- MapLibre 地图展示
- 国家和行政区边界显示
- 已打卡地点和当前搜索地点标记
- 地图拖动、缩放和区域点击
- 创建和查看地点访问记录
- 历史日志和成就分类统计
- 本地数据缓存与云端同步
- 用户名、个人资料和账号删除
- 国家/地区地理排行榜

## 3. Android 技术栈

- Kotlin Multiplatform
- Jetpack Compose Multiplatform
- Android SDK API 36
- `minSdk 26`
- MapLibre Android SDK
- Firebase Authentication
- Android Credential Manager / Google Identity Services
- SQLDelight 本地数据库
- Ktor HTTP 客户端
- Gradle 构建系统

后端服务使用 FastAPI、Firebase Admin SDK、PostgreSQL/Cloud SQL 和 Cloud Run，为 Android 应用提供认证验证、地点目录、achievement 同步和排行榜 API。

## 4. Android 应用架构

应用采用本地优先设计。用户创建的 achievement 先写入 SQLDelight 本地数据库，再由同步协调器上传到云端。Firebase Authentication 负责用户身份认证，客户端使用 Firebase ID Token 调用后端 API。

地图模块基于 MapLibre 实现，负责渲染底图、地点标记、搜索结果和行政区 GeoJSON 边界。Compose 页面负责搜索、个人资料、日志、排行榜和表单交互。

## 5. 重点难题

### Google 登录和多套签名证书

Google 登录同时依赖 Android 包名、Web OAuth Client ID、Android OAuth Client 和应用签名证书。通过 Google Play 分发时，应用使用 Play App Signing certificate，而不是本地 upload key，因此 Firebase 和 Google Cloud 需要配置对应的 SHA-1/SHA-256。项目还使用 Credential Manager 处理 Google 账号选择和 ID Token 获取。

### Google Play 发布和版本管理

Release AAB 必须使用 upload keystore 签名，并且每次上传都必须递增 `versionCode`。项目曾遇到 unsigned AAB、重复版本号、旧构建产物，以及本地签名和 Play App Signing 签名不同等问题。

当前 Android 版本为：

```text
versionCode: 9
versionName: 0.3.4
```

应用通过 Google Play Internal Testing 发布和验证。

## 6. 当前状态

- Android 核心功能已完成
- Firebase 登录、账号管理和云端同步已接入
- 地图、地点搜索、achievement 记录和排行榜已实现
- Release AAB 和 Google Play Internal Testing 流程已建立
- Google 登录仍需根据 Play App Signing certificate 和 Credential Manager 的实际错误信息进行最终验证

## 7. 后续改进方向

- 完善 Google 登录在不同 Android 版本和设备上的兼容性
- 增加更多 Android 自动化和端到端测试
- 增加离线同步冲突处理和失败重试
- 优化地图边界资源加载速度
- 完善账号删除、隐私政策和 Google Play 发布材料
