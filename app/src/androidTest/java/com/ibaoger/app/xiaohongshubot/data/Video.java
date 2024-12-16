package com.ibaoger.app.xiaohongshubot.data;

import org.jetbrains.annotations.NotNull;

// 视频信息
public class Video {
    private long videoId = 0;         // 视频编号
    private String shortTitle;        // 视频标题前12个字(查询效率)
    private String title;             // 视频标题
    private long creatorId;           // 作者编号
    private String publishTime;       // 发布时间(年月日时分秒)
    private long likeCount;            // 点赞数量
    private long favoriteCount;        // 收藏数量
    private long commentCount;         // 评论数量
    private long createTime;          // 创建时间(单位秒)
    private long updateTime;          // 更新时间(单位秒)
    private long deleteTime = 0;      // 删除时间(单位秒)
    private String remark = "";       // 备注

    public long getVideoId() {
        return videoId;
    }

    public void setVideoId(long videoId) {
        this.videoId = videoId;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public void setShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(long creatorId) {
        this.creatorId = creatorId;
    }

    public String getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(String publishTime) {
        this.publishTime = publishTime;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public long getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(long deleteTime) {
        this.deleteTime = deleteTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }


    // 复制视频信息
    public static void copy(@NotNull Video dst, @NotNull Video src) {
        dst.setVideoId(src.getVideoId());
        dst.setTitle(src.getTitle());
        dst.setCreatorId(src.getCreatorId());
        dst.setPublishTime(src.getPublishTime());
        dst.setLikeCount(src.getLikeCount());
        dst.setFavoriteCount(src.getFavoriteCount());
        dst.setCommentCount(src.getCommentCount());
        dst.setCreateTime(src.getCreateTime());
        dst.setUpdateTime(src.getUpdateTime());
        dst.setDeleteTime(src.getDeleteTime());
        dst.setRemark(src.getRemark());
    }


    // 复制视频信息，只拷贝必要字段
    public static void copySimple(@NotNull Video dst, @NotNull Video src) {
        if (src.getVideoId() > 0)
            dst.setVideoId(src.getVideoId());
        dst.setTitle(src.getTitle());
        dst.setCreatorId(src.getCreatorId());
        dst.setPublishTime(src.getPublishTime());
        dst.setLikeCount(src.getLikeCount());
        dst.setFavoriteCount(src.getFavoriteCount());
        dst.setCommentCount(src.getCommentCount());
        if (src.getUpdateTime() > dst.getUpdateTime())
            dst.setUpdateTime(src.getUpdateTime());
        if (src.getDeleteTime() > 0)
            dst.setDeleteTime(src.getDeleteTime());
        if (!src.getRemark().isEmpty())
            dst.setRemark(src.getRemark());
    }
}
