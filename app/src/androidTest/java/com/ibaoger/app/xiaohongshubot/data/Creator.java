package com.ibaoger.app.xiaohongshubot.data;

import org.jetbrains.annotations.NotNull;

// 创作者信息
public class Creator {
    private long creatorId = 0;        // 作者编号
    private String nickname;           // 作者昵称
    private String name = "";          // 作者姓名
    private String phone = "";         // 作者手机
    private String email = "";         // 作者邮箱
    private String xiaohongshuId;      // 小红书号
    private String ipLocation;         // IP属地
    private String introduction;       // 作者简介
    private String tags;               // 标签
    private long noteCount = 0;         // 笔记数量
    private long followCount = 0;       // 关注数量
    private long fansCount = 0;          // 粉丝数量
    private long likeCount = 0;         // 获赞数量
    private long favoriteCount = 0;     // 收藏数量
    private short isIntroductionParsed = 0; // 是否解析过简介
    private long createTime;           // 创建时间(单位秒)
    private long updateTime;           // 更新时间(单位秒)
    private long deleteTime = 0;       // 删除时间(单位秒)
    private String remark = "";        // 备注

    public long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(long creatorId) {
        this.creatorId = creatorId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getXiaohongshuId() {
        return xiaohongshuId;
    }

    public void setXiaohongshuId(String xiaohongshuId) {
        this.xiaohongshuId = xiaohongshuId;
    }

    public String getIpLocation() {
        return ipLocation;
    }

    public void setIpLocation(String ipLocation) {
        this.ipLocation = ipLocation;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public long getNoteCount() {
        return noteCount;
    }

    public void setNoteCount(long noteCount) {
        this.noteCount = noteCount;
    }

    public long getFollowCount() {
        return followCount;
    }

    public void setFollowCount(long followCount) {
        this.followCount = followCount;
    }

    public long getFansCount() {
        return fansCount;
    }

    public void setFansCount(long fansCount) {
        this.fansCount = fansCount;
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

    // 复制创作者信息
    public static void copy(@NotNull Creator dst, @NotNull Creator src) {
        dst.setCreatorId(src.getCreatorId());
        dst.setNickname(src.getNickname());
        dst.setName(src.getName());
        dst.setPhone(src.getPhone());
        dst.setEmail(src.getEmail());
        dst.setXiaohongshuId(src.getXiaohongshuId());
        dst.setIpLocation(src.getIpLocation());
        dst.setIntroduction(src.getIntroduction());
        dst.setTags(src.getTags());
        dst.setNoteCount(src.getNoteCount());
        dst.setFollowCount(src.getFollowCount());
        dst.setFansCount(src.getFansCount());
        dst.setLikeCount(src.getLikeCount());
        dst.setFavoriteCount(src.getFavoriteCount());
        dst.setCreateTime(src.getCreateTime());
        dst.setUpdateTime(src.getUpdateTime());
        dst.setDeleteTime(src.getDeleteTime());
        dst.setRemark(src.getRemark());
    }

    // 复制创作者信息，只拷贝必要字段
    public static void copySimple(@NotNull Creator dst, @NotNull Creator src) {
        if (src.getCreatorId() > 0)
            dst.setCreatorId(src.getCreatorId());
        dst.setNickname(src.getNickname());
        if (!src.getName().isEmpty())
            dst.setName(src.getName());
        if (!src.getPhone().isEmpty())
            dst.setPhone(src.getPhone());
        if (!src.getEmail().isEmpty())
            dst.setEmail(src.getEmail());
        dst.setXiaohongshuId(src.getXiaohongshuId());
        dst.setIpLocation(src.getIpLocation());
        dst.setIntroduction(src.getIntroduction());
        dst.setTags(src.getTags());
        dst.setNoteCount(src.getNoteCount());
        dst.setFollowCount(src.getFollowCount());
        dst.setFansCount(src.getFansCount());
        dst.setLikeCount(src.getLikeCount());
        dst.setFavoriteCount(src.getFavoriteCount());
        if (src.getUpdateTime() > dst.getUpdateTime())
            dst.setUpdateTime(src.getUpdateTime());
        if (src.getDeleteTime() > 0)
            dst.setDeleteTime(src.getDeleteTime());
        if (!src.getRemark().isEmpty())
            dst.setRemark(src.getRemark());
    }
}
