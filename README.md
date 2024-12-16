# 使用说明
小红书自动化采集项目。

## 运行环境

Android Studio 2023 加载项目，调试运行 XiaohongshuCreatorTest。

为了方便第一次运行，已注释掉保存数据库相关逻辑。

## 项目配置

### 采集关键词

XiaohongshuCreatorTest.java - testXhsApp - keywords 变量。

### 保存数据库

db/xhs.sql

Config.java - DB_URL, DB_USER, DB_PASSWORD 变量。

### 适配版本

8.58.0 - 8.99.99

XiaohongshuCreatorTest.java - checkAppVersion 方法。



## TODO

- [x] 视频标题、点赞、收藏

- [x] 博主昵称、小红书号、IP属地、简介、关注、粉丝、点赞、收藏、笔记数量

- [x] 项目运行期间，禁止手机休眠
