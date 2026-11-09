# 漫步者连接弹窗 EdifierPopup

蓝牙连接漫步者（名称含 EDIFIER / 漫步者）耳机后，自动在屏幕底部弹出带动画的连接卡片。

## 打包 APK（三选一）

**A. Android Studio（最简单）**
1. 安装 Android Studio，File → Open 选择本目录
2. 等待 Gradle 同步完成
3. 手机开启开发者模式 + USB 调试，连接电脑，点 Run
   或：Build → Build APK(s)，拷贝 `app/build/outputs/apk/debug/app-debug.apk` 到手机安装

**B. GitHub Actions（不装任何软件）**
1. 把本目录上传到你自己的 GitHub 仓库（main 分支）
2. 进入 Actions → Build APK → Run workflow
3. 完成后在页面底部 Artifacts 下载 `EdifierPopup-debug-apk`，解压得到 APK

**C. 命令行**：安装 JDK 17 + Android SDK + Gradle 8.7 后执行 `gradle :app:assembleDebug`

## 首次使用（vivo）
1. 打开 App，依次点 1、2、3 完成授权
2. 在应用详情页里放行：后台弹出界面、自启动、电池「后台高耗电/不限制」
3. 点「4. 开始监听」，通知栏出现常驻通知即成功
4. 点「测试弹窗效果」先看效果，再连耳机验证

## 注意
- 电量通过系统隐藏接口读取总电量，部分机型/耳机读不到会显示 `--`
- 左右耳、充电盒分别电量需要漫步者私有协议，本项目未实现
- 服务被系统杀掉后弹窗不会出现，请务必设置好 vivo 的后台与自启动权限
