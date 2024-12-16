/*
 Navicat Premium Data Transfer

 Source Server         : 192.168.1.100
 Source Server Type    : MySQL
 Source Server Version : 80403 (8.4.3)
 Source Host           : 192.168.1.100:3306
 Source Schema         : xhs

 Target Server Type    : MySQL
 Target Server Version : 80403 (8.4.3)
 File Encoding         : 65001

 Date: 16/12/2024 09:03:03
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for creator
-- ----------------------------
DROP TABLE IF EXISTS `creator`;
CREATE TABLE `creator`  (
  `creator_id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '作者编号',
  `nickname` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '作者昵称',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '作者姓名',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '作者手机',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '作者邮箱',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '对作者的称呼',
  `xiaohongshu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小红书号',
  `ip_location` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IP属地',
  `introduction` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '作者简介',
  `tags` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '标签',
  `note_count` int UNSIGNED NULL DEFAULT 0 COMMENT '笔记数量',
  `follow_count` int UNSIGNED NULL DEFAULT 0 COMMENT '关注数量',
  `fans_count` int UNSIGNED NULL DEFAULT 0 COMMENT '粉丝数量',
  `like_count` int UNSIGNED NULL DEFAULT 0 COMMENT '获赞数量',
  `favorite_count` int UNSIGNED NULL DEFAULT 0 COMMENT '收藏数量',
  `is_introduction_parsed` tinyint UNSIGNED NULL DEFAULT 0 COMMENT '是否解析过作者简介',
  `is_nickname_parsed` tinyint UNSIGNED NULL DEFAULT 0 COMMENT '是否解析过作者昵称',
  `is_sent_mail` tinyint UNSIGNED NULL DEFAULT 0 COMMENT '是否发送过邮件(0:初始 1:已发送 2:发送失败)',
  `create_time` int UNSIGNED NOT NULL COMMENT '创建时间(单位秒)',
  `update_time` int UNSIGNED NOT NULL COMMENT '更新时间(单位秒)',
  `delete_time` int UNSIGNED NULL DEFAULT 0 COMMENT '删除时间(单位秒)',
  `remark` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`creator_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5281 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '创作者信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video
-- ----------------------------
DROP TABLE IF EXISTS `video`;
CREATE TABLE `video`  (
  `video_id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '视频编号',
  `short_title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视频标题前32个字(查询效率)',
  `title` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视频标题',
  `creator_id` int UNSIGNED NOT NULL COMMENT '作者编号',
  `publish_time` datetime NOT NULL COMMENT '发布时间(年月日时分秒)',
  `like_count` int UNSIGNED NULL DEFAULT 0 COMMENT '点赞数量',
  `favorite_count` int UNSIGNED NULL DEFAULT 0 COMMENT '收藏数量',
  `comment_count` int UNSIGNED NULL DEFAULT 0 COMMENT '评论数量',
  `create_time` int UNSIGNED NOT NULL COMMENT '创建时间(单位秒)',
  `update_time` int UNSIGNED NOT NULL COMMENT '更新时间(单位秒)',
  `delete_time` int UNSIGNED NULL DEFAULT 0 COMMENT '删除时间(单位秒)',
  `remark` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`video_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8327 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '视频信息' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
