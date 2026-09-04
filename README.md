# Moonlight V+ TCL 个人适配版

## 来源

本仓库 Fork 自 [qiin2333/moonlight-vplus](https://github.com/qiin2333/moonlight-vplus)。上游项目基于 [Moonlight Android](https://github.com/moonlight-stream/moonlight-android)。

## 解决的问题

moonlight-vplus 在部分 TCL 电视上无法正常安装。目前该fork能正常在tcl电视上安装并使用

现有版本和权限对比表明，该问题与应用声明的 `REQUEST_INSTALL_PACKAGES`、`INSTALL_PACKAGES` 两项 APK 安装权限高度相关，但尚无逐项对照测试证明它们是唯一原因。

此外，电视端直接连接 GitHub 获取应用更新通常不稳定，因此这个个人适配版不再保留应用内更新和 APK 安装功能。

## 解决方法

移除两项 APK 安装权限以及完整的应用内更新、下载和安装流程，同时保留应用运行所需的权限，并将其他功能仍需使用的图片下载代理和崩溃报告分享能力从更新模块中分离出来。

## 本 Fork 的改动

- 移除应用内版本检查、更新下载和 APK 安装功能，以及相关的设置入口、更新弹窗和下载完成广播接收器。
- 移除 `REQUEST_INSTALL_PACKAGES` 和 `INSTALL_PACKAGES` 权限，保留应用运行所需的 `MANAGE_EXTERNAL_STORAGE` 和 `HIGH_SAMPLING_RATE_SENSORS` 权限。
- 将仍被图片下载功能使用的 GitHub 代理逻辑从更新模块中独立出来。
- 将原用于更新 APK 的 `FileProvider` 调整为仅用于分享崩溃报告。
