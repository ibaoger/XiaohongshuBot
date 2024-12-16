package com.ibaoger.app.xiaohongshubot;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.ibaoger.app.xiaohongshubot.data.Creator;
import com.ibaoger.app.xiaohongshubot.data.Video;
import com.ibaoger.app.xiaohongshubot.utils.LogUtils;
import com.ibaoger.app.xiaohongshubot.utils.MySQLUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// 收集小红书的创作者信息
//
// 依赖工具(必备): UIAutomatorViewer.bat (Android SDK <= 25) 给java_exe变量设置java1.8路径，双击启动即可
//
// 避坑指南
//    慎用findObjects()会包含隐藏元素，尽量使用getChildren()代替
//    UI Automator 框架是黑盒测试，尽量不依赖具体的控件ID，而是依赖控件的层级结构
//    定位元素时，尽量使用唯一属性，不依赖其他元素
//    慎用suc=device.pressBack()，suc可能会返回错误状态，导致后续操作失败
//    使用device.wait()之前，可以先查询一下元素，避免等待时间
//
// 实时日志: logcat - package:com.ibaoger.app.xiaohongshubot & level:info
// 日志文件: /sdcard/Download/AiBot/Xiaohongshu/xhs_20210801_1627800000000.txt
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 18)
public class XiaohongshuCreatorTest {
    private static final String TAG = "XHSBot";
    private Logger logger;
    private Context context;
    // 小红书APP的包名，使用ApkAnalyzer查看
    private static final String XIAO_HONG_SHU_PACKAGE = "com.xingin.xhs";
    private static final int LAUNCH_APP_TIMEOUT = 5000;
    private static final int REFRESH_PAGE_TIMEOUT = 3000;
    private static final int DATABASE_QUERY_TIMEOUT = 3000;
    private static final int SLEEP_AFTER_CLICK = 150;
    private static final int SLEEP_AFTER_INPUT = 200;
    private static final int SLEEP_AFTER_HTTP_REQUEST = 1000;
    private static final int SLEEP_AFTER_SWIPE = 500;
    private static final int SLEEP_AFTER_SWITCH_PAGE = 600;
    private UiDevice device;
    private int deviceWidth;
    private int deviceHeight;
    private static MySQLUtils database = new MySQLUtils();
    private Video video = new Video();
    private Creator creator = new Creator();
    // 标志位，用于判断是否需要继续遍历视频列表
    private boolean isContinueTraversingVideoFlag = true;
    private PowerManager.WakeLock wakeLock;
    private boolean running = true;

    // 检查权限(需要先安装运行app，并授予权限，再运行测试)
    private boolean checkPermissions() {
        BufferedWriter bufferedWriter;
        String logFolderPath = LogUtils.getLogFolderPath();
        File checkPermissionFile = new File(logFolderPath + "check_permission.txt");
        if (!checkPermissionFile.exists()) {
            try {
                File parent = checkPermissionFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                checkPermissionFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(checkPermissionFile, true));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (bufferedWriter != null) {
            try {
                bufferedWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    // 测试开始
    @Before
    public void testSetup() {
        // 检查权限
        if (!checkPermissions()) {
            throw new AssertionError("检查存储权限失败，需要先安装运行app，并授予权限，再运行测试");
        }
        // 获取上下文
        context = ApplicationProvider.getApplicationContext();
        // 获取设备实例
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // 初始化日志
        LogUtils.initLog(context);
        logger = XLog.tag(TAG).build();
        // 获取设备屏幕宽高
        deviceWidth = device.getDisplayWidth();
        deviceHeight = device.getDisplayHeight();
        // 设置手机竖屏
        if (deviceWidth > deviceHeight) {
            try {
                device.setOrientationNatural();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        // 保持屏幕常亮
        try {
            Context context = InstrumentationRegistry.getInstrumentation().getContext();
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG + ":WakeLock");
            wakeLock.acquire();
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wakeUp();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 测试结束
    @After
    public void testCleanup() {
        // 释放屏幕常亮
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // 测试小红书APP
    @Test
    public void testXhsApp() {
        logger.i("测试小红书APP");
        assert checkAppVersion();
        while (running) {
            List<String> keywords = new ArrayList<>(Arrays.asList("健康"));
            for (String keyword : keywords) {
                isContinueTraversingVideoFlag = true;
                assert openApp();
                if (openSearchPage()) {
                    if (searchKeyword(keyword)) {
                        while (isContinueTraversingVideoFlag) {
                            traverseVideos(keyword);
                        }
                    } else {
                        logger.i("searchKeyword() failed");
                    }
                } else {
                    logger.i("openSearchPage() failed");
                }
            }
            sleep(1000 * 10);
        }
    }

    // 测试数据库连接
    //@Test
    public void testDatabase() {
        logger.i("测试数据库连接");
        int rows = 0;
        long createTime = System.currentTimeMillis() / 1000;
        Creator c = new Creator();
        c.setCreatorId(0);
        c.setNickname("昵称");
        c.setName("姓名");
        c.setPhone("13012345678");
        c.setEmail("123@abc.com");
        c.setXiaohongshuId("123456");
        c.setIpLocation("浙江");
        c.setIntroduction("简介");
        c.setTags("女|20岁|美食|旅行");
        c.setFollowCount(11);
        c.setFansCount(22);
        c.setLikeCount(33);
        c.setFavoriteCount(44);
        c.setCreateTime(createTime);
        c.setUpdateTime(createTime);
        c.setDeleteTime(0);
        c.setRemark("");

        List<Creator> creators = this.getCreatorByNickname(c.getNickname());
        assert creators.isEmpty();

        creators = this.getCreatorByNicknameAndXiaohongshuId(c.getNickname(), c.getXiaohongshuId());
        assert creators.isEmpty();

        rows = this.insertCreator(c);
        assert rows > 0;

        creators = this.getCreatorByNicknameAndXiaohongshuId(c.getNickname(), c.getXiaohongshuId());
        assert !creators.isEmpty();

        c = creators.get(0);
        long updateTime = System.currentTimeMillis() / 1000;
        c.setUpdateTime(updateTime);
        rows = this.updateCreator(c);
        assert rows > 0;

        Video v = new Video();
        v.setVideoId(0);
        v.setShortTitle("短标题");
        v.setTitle("标题");
        v.setCreatorId(c.getCreatorId());
        v.setPublishTime("2021-01-01 00:00:00");
        v.setLikeCount(55);
        v.setFavoriteCount(66);
        v.setCommentCount(77);
        v.setCreateTime(createTime);
        v.setUpdateTime(createTime);
        v.setDeleteTime(0);
        v.setRemark("");

        List<Video> videos = this.getVideoByTitle(v.getShortTitle(), true);
        assert videos.isEmpty();

        videos = this.getVideoByTitleAndCreatorId(v.getShortTitle(), true, v.getCreatorId());
        assert videos.isEmpty();

        rows = this.insertVideo(v);
        assert rows > 0;

        videos = this.getVideoByTitleAndCreatorId(v.getShortTitle(), true, v.getCreatorId());
        assert !videos.isEmpty();

        v = videos.get(0);
        updateTime = System.currentTimeMillis() / 1000;
        v.setUpdateTime(updateTime);
        rows = this.updateVideo(v);
        assert rows > 0;
    }

    static class AppInfo {
        String packageName;
        int versionCode = 0;
        String versionName;
    }

    // 检测小红书版本号
    private boolean checkAppVersion() {
        logger.i("checkAppVersion()");
        AppInfo appInfo = getAppVersion(XIAO_HONG_SHU_PACKAGE);
        logger.i("小红书APP版本号: %d - %s", appInfo.versionCode, appInfo.versionName);
        int minCompatibleVersionCode = 8580000;
        int maxCompatibleVersionCode = 8999999;
        if (minCompatibleVersionCode <= appInfo.versionCode && appInfo.versionCode <= maxCompatibleVersionCode) {
            return true;
        }
        return false;
    }

    private @NotNull AppInfo getAppVersion(@NotNull String packageName) {
        AppInfo appInfo = new AppInfo();
        try {
            Context context = ApplicationProvider.getApplicationContext();
            PackageManager packageManager = context.getPackageManager();
            appInfo.packageName = packageName;
            appInfo.versionCode = packageManager.getPackageInfo(packageName, 0).versionCode;
            appInfo.versionName = packageManager.getPackageInfo(packageName, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            logger.e("get app version failed, %s", packageName);
        }
        return appInfo;
    }

    // 打开小红书APP，进入首页
    // 所有的错误均抛出异常
    private boolean openApp() {
        logger.i("openApp()");
        // 进入手机桌面
        device.pressHome();
        final String launcherPackage = device.getLauncherPackageName();
        if (launcherPackage == null) {
            logger.e("手机启动器的包名为空");
            throw new AssertionError("手机启动器的包名为空");
        }
        Boolean hasObject = device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_APP_TIMEOUT);
        if (!hasObject) {
            logger.e("进入手机桌面，等待超时，%s", launcherPackage);
            throw new AssertionError("进入手机桌面，等待超时，" + launcherPackage);
        }

        logger.i("进入手机桌面");

        // 打开小红书APP
        Context context = ApplicationProvider.getApplicationContext();
        if (context == null) {
            logger.e("手机系统上下文为空");
            throw new AssertionError("手机系统上下文为空");
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            logger.e("手机包管理器为空");
            throw new AssertionError("手机包管理器为空");
        }

        final Intent intent = packageManager.getLaunchIntentForPackage(XIAO_HONG_SHU_PACKAGE);
        if (intent == null) {
            logger.e("程序包 %s 的启动界面为空", XIAO_HONG_SHU_PACKAGE);
            throw new AssertionError("程序包 " + XIAO_HONG_SHU_PACKAGE + " 的启动界面为空");
        }

        logger.i("启动小红书APP");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        hasObject = device.wait(Until.hasObject(By.pkg(XIAO_HONG_SHU_PACKAGE).depth(0)), LAUNCH_APP_TIMEOUT);
        if (!hasObject) {
            logger.e("启动小红书APP，等待首页超时，%s", XIAO_HONG_SHU_PACKAGE);
            throw new AssertionError("启动小红书APP，等待首页超时，" + XIAO_HONG_SHU_PACKAGE);
        }

        logger.i("进入小红书首页");
        return true;
    }

    // 发现页面，点击搜索按钮，等待搜索页面
    // 所有的错误返回false，不要抛出异常
    private boolean openSearchPage() {
        logger.i("openSearchPage()");
        // 小红书
        String currPackageName = device.getCurrentPackageName();
        if (!XIAO_HONG_SHU_PACKAGE.equals(currPackageName)) {
            logger.e("小红书不在前台，当前运行的是 %s", currPackageName);
            return false;
        }

        // 首页-发现页面
        // 关键字 "关注" "发现" "附近" + "推荐" （使用 UIAutomatorViewer.bat 查看）
        logger.i("首页，等待加载");
        Boolean hasObject;
        logger.d("首页，等待关注页面加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("关注")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("首页，找不到关注页面");
            return false;
        }
        logger.d("首页，等待发现页面加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("发现")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("首页，找不到发现页面");
            return false;
        }
        logger.d("首页，等待附近页面加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("附近")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("首页，找不到附近页面");
            return false;
        }
        logger.i("首页(默认是发现页面)");

        UiObject2 followTab = device.findObject(By.clazz("android.widget.TextView").text("关注"));
        UiObject2 discoverTab = device.findObject(By.clazz("android.widget.TextView").text("发现"));
        UiObject2 nearbyTab = device.findObject(By.clazz("android.widget.TextView").text("附近"));
        if (followTab == null || discoverTab == null || nearbyTab == null) {
            logger.e("发现页面，找不到关注发现附近按钮，不应该出现这种情况");
            return false;
        }

        // 首页-搜索按钮
        // 关键字 "搜索"
        logger.i("发现页面，等待加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.Button").desc("搜索")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("发现页面，找不到搜索按钮，首页布局可能有变化");
            return false;
        }
        logger.i("发现页面");

        UiObject2 homeSearchButton = device.findObject(By.clazz("android.widget.Button").desc("搜索"));
        if (homeSearchButton == null) {
            logger.e("发现页面，找不到搜索按钮，不应该出现这种情况");
            return false;
        }
        logger.i("发现页面，点击搜索按钮，等待加载搜索页面");
        homeSearchButton.click();
        sleep(SLEEP_AFTER_SWITCH_PAGE);

        return true;
    }

    // 搜索页面，筛选关键词，等待视频列表
    // 所有的错误返回false，不要抛出异常
    private boolean searchKeyword(@NotNull String keyword) {
        logger.i("searchKeyword(%s)", keyword);
        // 搜索页面
        // 关键字 "搜索" （使用 UIAutomatorViewer.bat 查看）
        logger.i("搜索页面，等待加载");
        Boolean hasObject;
        logger.d("搜索页面，等待搜索框加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.EditText").text("搜索, ")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("搜索页面，找不到搜索框，关键词 %s", keyword);
            return false;
        }
        logger.d("搜索页面，等待搜索按钮加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.Button").text("搜索")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("搜索页面，找不到搜索按钮，关键词 %s", keyword);
            return false;
        }
        logger.i("搜索页面");

        UiObject2 searchInput = device.findObject(By.clazz("android.widget.EditText").text("搜索, "));
        UiObject2 searchButton = device.findObject(By.clazz("android.widget.Button").text("搜索"));
        if (searchInput == null || searchButton == null) {
            logger.e("发现页面，找不到搜索框和搜索按钮，不应该出现这种情况，关键词 %s", keyword);
            return false;
        }

        logger.i("搜索页面，输入搜索词: %s", keyword);
        searchInput.setText(keyword);
        sleep(SLEEP_AFTER_INPUT);

        logger.i("搜索页面，点击搜索按钮");
        searchButton.click();
        sleep(SLEEP_AFTER_SWITCH_PAGE);

        // 搜索页面
        // 关键字 "筛选" （使用 UIAutomatorViewer.bat 查看）
        logger.i("搜索页面，筛选按钮，等待加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("筛选")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("搜索页面，找不到筛选按钮，关键词 %s", keyword);
            return false;
        }
        logger.i("搜索页面，筛选按钮");

        UiObject2 filterText = device.findObject(By.clazz("android.widget.TextView").text("筛选"));
        if (filterText == null) {
            logger.e("搜索页面，找不到筛选按钮，不应该出现这种情况，关键词 %s", keyword);
            return false;
        }
        logger.i("搜索页面，点击筛选按钮");
        filterText.click();
        sleep(SLEEP_AFTER_SWITCH_PAGE);

        // 搜索页面
        // 关键字 "排序依据" "视频" "一周内" "未看过" "收起" （使用 UIAutomatorViewer.bat 查看）
        logger.i("搜索页面，筛选弹窗，查找排序依据按钮");
        UiObject2 sortByView = device.findObject(By.clazz("android.widget.TextView").text("排序依据"));
        if (sortByView == null) {
            logger.w("搜索页面，筛选弹窗，找不到排序依据，关键词 %s", keyword);

            logger.i("搜索页面，筛选弹窗，等待排序依据加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("排序依据")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.e("搜索页面，筛选弹窗，找不到排序依据，关键词 %s", keyword);
                return false;
            }
        }

        logger.i("搜索页面，筛选弹窗，查找收起按钮");
        UiObject2 closeView = device.findObject(By.clazz("android.widget.TextView").text("收起"));
        if (closeView == null) {
            logger.w("搜索页面，筛选弹窗，找不到收起，关键词 %s", keyword);

            logger.d("搜索页面，筛选弹窗，等待收起按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("收起")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.e("搜索页面，筛选弹窗，找不到收起按钮，关键词 %s", keyword);
                return false;
            }
        }
        logger.i("搜索页面，筛选弹窗");

        UiObject2 noteTypeFilter = device.findObject(By.clazz("android.widget.TextView").text("笔记类型"));
        if (noteTypeFilter == null) {
            logger.e("搜索页面，筛选弹窗，找不到筛选条件，可能是布局有变化，关键词 %s", keyword);
            return false;
        }

        UiObject2 videoFilter = noteTypeFilter.getParent().findObject(By.clazz("android.widget.TextView").text("视频"));
        UiObject2 weekFilter = device.findObject(By.clazz("android.widget.TextView").text("一周内"));
        UiObject2 unseenFilter = device.findObject(By.clazz("android.widget.TextView").text("未看过"));
        UiObject2 packUpFilter = device.findObject(By.clazz("android.widget.TextView").text("收起"));
        if (videoFilter == null || weekFilter == null || unseenFilter == null || packUpFilter == null) {
            logger.e("搜索页面，筛选弹窗，找不到筛选条件，关键词 %s videoFilter=%s weekFilter=%s unseenFilter=%s packUpFilter=%s", keyword, videoFilter, weekFilter, unseenFilter, packUpFilter);
            return false;
        }
        logger.i("搜索页面，筛选弹窗，选择筛选条件: 视频 一周内 未看过");

        logger.d("搜索页面，筛选弹窗，点击视频筛选条件");
        videoFilter.click();
        sleep(SLEEP_AFTER_CLICK);

        logger.d("搜索页面，筛选弹窗，点击一周内筛选条件");
        weekFilter.click();
        sleep(SLEEP_AFTER_CLICK);

        logger.d("搜索页面，筛选弹窗，点击未看过筛选条件");
        unseenFilter.click();
        sleep(SLEEP_AFTER_CLICK);

        logger.d("搜索页面，筛选弹窗，点击收起按钮");
        packUpFilter.click();
        sleep(SLEEP_AFTER_HTTP_REQUEST);

        return true;
    }

    // 搜索页面，视频列表，遍历视频列表，收集博主信息
    // 所有的错误返回false，不要抛出异常
    private boolean traverseVideos(@NotNull String keyword) {
        logger.i("traverseVideos()");
        Boolean hasObject;

        // 搜索页面-视频列表
        // 关键字 "搜索" "综合" （使用 UIAutomatorViewer.bat 查看）
        logger.i("搜索页面，视频列表，等待加载，关键词 %s", keyword);

        logger.d("搜索页面，视频列表，等待搜索按钮加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("搜索")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("搜索页面，视频列表，找不到搜索按钮，关键词 %s", keyword);
            return false;
        }

        logger.d("搜索页面，视频列表，等待综合分类加载");
        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("综合")), REFRESH_PAGE_TIMEOUT);
        if (!hasObject) {
            logger.e("搜索页面，视频列表，找不到综合分类，关键词 %s", keyword);
            return false;
        }
        logger.i("搜索页面，视频列表");

        // 继续遍历视频列表
        isContinueTraversingVideoFlag = false;

        // "综合"文字的底部Y坐标，用于滑动
        int compositeTextBottomY = 0;
        UiObject2 compositeText = device.findObject(By.clazz("android.widget.TextView").text("综合"));
        if (compositeText != null) {
            compositeTextBottomY = compositeText.getParent().getParent().getVisibleBounds().bottom;
            logger.i("搜索页面，视频列表，综合文字的底部Y坐标 %d", compositeTextBottomY);
        }

        // 所有完整的视频元素中，最靠近屏幕底部的视频元素的底部Y坐标，用于滑动
        int videoRootLayoutBottomY = 0;

        // 遍历 ImageView 元素，找出符合条件的博主视频
        // - RelativeLayout
        //   - ImageView (视频封面) 定位基准
        //   - ImageView (播放按钮)
        //   - TextView (视频标题)
        //   - LinearLayout
        //     - View (博主头像)
        //     - LinearLayout
        //       - TextView (博主昵称)
        //       - TextView (发布时间)
        //     - FrameLayout
        //       - ImageView (点赞按钮)
        //       - TextView (点赞数)
        List<UiObject2> videoCoverImages = device.findObjects(By.clazz("android.widget.ImageView").enabled(true));
        if (videoCoverImages.isEmpty()) {
            logger.e("搜索页面，视频列表，找不到任何视频封面，关键词 %s", keyword);
            return false;
        }

        logger.d("搜索页面，视频列表，找到 %d 个视频封面", videoCoverImages.size());
        for (UiObject2 videoCoverImage : videoCoverImages) {
            try {
                // 使用 device 查找的元素可能包含其他应用的元素，需要过滤掉
                if (!videoCoverImage.getApplicationPackage().equals(XIAO_HONG_SHU_PACKAGE)) {
                    continue;
                }
                // 使用 device 查找的元素可能包含过期的元素，需要过滤掉 StaleObjectException
                videoCoverImage.getVisibleBounds();
            } catch (Exception e) {
                continue;
            }

            logger.i("搜索页面，视频列表，任意层级的子元素 ImageView， class=%s", videoCoverImage.getClassName());

            // 创建一个新的对象
            creator = new Creator();
            video = new Video();

            // 过滤掉尺寸太小的视频封面(横屏、竖屏)
            int width = videoCoverImage.getVisibleBounds().width();
            int height = videoCoverImage.getVisibleBounds().height();
            if (width < deviceWidth * 0.35 || height < width * 0.73) {
                continue;
            }

            logger.i("搜索页面，视频列表，符合条件的子元素 ImageView， class=%s", videoCoverImage.getClassName());

            UiObject2 videoRootLayout = videoCoverImage.getParent();
            // 判断一级子元素个数，getChildren不包括隐藏元素，getChildCount包括隐藏元素
            if (videoRootLayout.getChildren().size() != 4) {
                logger.w("搜索页面，视频列表，视频元素不完整，RelativeLayout 的一级子元素 %d/4 个，关键词 %s", videoRootLayout.getChildren().size(), keyword);
                videoRootLayout.getChildren().forEach(child -> logger.i("搜索页面，视频列表，视频元素 class=%s text=%s desc=%s", child.getClassName(), child.getText(), child.getContentDescription()));
                continue;
            }

            // 使用 getChildren 遍历一级子元素
            logger.d("搜索页面，视频列表，遍历封面、标题、博主，RelativeLayout 的一级子元素");
            List<UiObject2> videoRootLayoutChildren = videoRootLayout.getChildren();
            UiObject2 videoPlayButton = null;
            UiObject2 videoTitle = null;
            UiObject2 videoLayout = null;
            for (UiObject2 child : videoRootLayoutChildren) {
                if (child.getClassName().equals("android.widget.ImageView")) {
                    videoPlayButton = child;
                } else if (child.getClassName().equals("android.widget.TextView")) {
                    videoTitle = child;
                } else if (child.getClassName().equals("android.widget.LinearLayout")) {
                    videoLayout = child;
                }
            }
            if (videoPlayButton == null || videoTitle == null || videoLayout == null) {
                logger.e("搜索页面，视频列表，RelativeLayout 的一级子元素，至少有一个为空，关键词 %s videoPlayButton=%s, videoTitle=%s, videoLayout=%s",
                        keyword, videoPlayButton, videoTitle, videoLayout);
                continue;
            }

            // 视频标题前12个字(查询效率)，去掉换行，否则可能查询不到；不能使用视频详情页的标题，因为两者可能不一样
            String videoShortTitle = videoTitle.getText().replaceAll("[\r\n]", "");
            if (videoShortTitle.length() > 32) {
                videoShortTitle = videoShortTitle.substring(0, 32);
            }

//            // 判断视频标题是否在数据库中
//            logger.d("搜索页面，视频列表，根据标题查询视频是否存在 %s", videoTitle.getText().replaceAll("[\r\n]", ""));
//            List<Video> videos = this.getVideoByTitle(videoShortTitle, true);
//            if (!videos.isEmpty()) {
//                logger.i("搜索页面，视频列表，视频标题 %s 已存在，跳过", videoTitle.getText().replaceAll("[\r\n]", ""));
//
//                // 所有完整的视频元素中，最靠近屏幕底部的视频元素的底部Y坐标
//                if (videoRootLayoutBottomY < videoRootLayout.getVisibleBounds().bottom) {
//                    videoRootLayoutBottomY = videoRootLayout.getVisibleBounds().bottom;
//                    logger.i("搜索页面，视频列表，视频元素的底部Y坐标 %d", videoRootLayoutBottomY);
//                }
//
//                continue;
//            }
//            logger.d("搜索页面，视频列表，查询到视频个数 %d", videos.size());

            // 判断一级子元素个数，getChildren不包括隐藏元素，getChildCount包括隐藏元素
            if (videoLayout.getChildren().size() != 3) {
                logger.e("搜索页面，视频列表，RelativeLayout - LinearLayout 的一级子元素个数应该有3个，实际个数是 %d", videoLayout.getChildren().size());
                videoLayout.getChildren().forEach(child -> logger.i("搜索页面，视频列表，RelativeLayout - LinearLayout 的一级子元素 class=%s text=%s desc=%s", child.getClassName(), child.getText(), child.getContentDescription()));
                continue;
            }
            // 使用 getChildren 遍历一级子元素
            logger.d("搜索页面，视频列表，遍历头像、昵称、点赞，RelativeLayout - LinearLayout 的一级子元素");
            List<UiObject2> videoLayoutChildren = videoLayout.getChildren();
            UiObject2 creatorAvatar = null;
            UiObject2 creatorLayout = null;
            UiObject2 likeLayout = null;
            for (UiObject2 child : videoLayoutChildren) {
                if (child.getClassName().equals("android.view.View")) {
                    creatorAvatar = child;
                } else if (child.getClassName().equals("android.widget.LinearLayout")) {
                    creatorLayout = child;
                } else if (child.getClassName().equals("android.widget.FrameLayout")) {
                    likeLayout = child;
                }
            }
            if (creatorAvatar == null || creatorLayout == null || likeLayout == null) {
                logger.e("搜索页面，视频列表，RelativeLayout - LinearLayout 的一级子元素，至少有一个为空，关键词 %s creatorAvatar=%s, creatorLayout=%s, likeLayout=%s",
                        keyword, creatorAvatar, creatorLayout, likeLayout);
                continue;
            }
            // 判断一级子元素个数，getChildren()不包括隐藏元素，getChildCount()包括隐藏元素
            if (creatorLayout.getChildren().size() != 2) {
                logger.e("搜索页面，视频列表，RelativeLayout - LinearLayout - LinearLayout 的一级子元素个数 %d/2，关键词 %s", creatorLayout.getChildren().size(), keyword);
                creatorLayout.getChildren().forEach(child -> logger.i("搜索页面，视频列表，RelativeLayout - LinearLayout - LinearLayout 的一级子元素 class=%s text=%s desc=%s", child.getClassName(), child.getText(), child.getContentDescription()));
                continue;
            }
            // 判断一级子元素个数，getChildren()不包括隐藏元素，getChildCount()包括隐藏元素
            if (likeLayout.getChildren().size() != 2) {
                logger.e("搜索页面，视频列表，RelativeLayout - LinearLayout - FrameLayout 的一级子元素个数 %d/2，关键词 %s", likeLayout.getChildren().size(), keyword);
                likeLayout.getChildren().forEach(child -> logger.i("搜索页面，视频列表，RelativeLayout - LinearLayout - FrameLayout 的一级子元素 class=%s text=%s desc=%s", child.getClassName(), child.getText(), child.getContentDescription()));
                continue;
            }
            // 使用 findObject() 会遍历所有子元素，这里实际需要的是一级子元素
            logger.d("搜索页面，视频列表，遍历昵称、发布日期，RelativeLayout - LinearLayout - LinearLayout 的一级子元素");
            List<UiObject2> creatorLayoutChildren = creatorLayout.getChildren();
            UiObject2 creatorNickname = null;
            UiObject2 publishTime = null;
            for (UiObject2 child : creatorLayoutChildren) {
                if (child.getClassName().equals("android.widget.TextView")) {
                    if (child.isClickable()) {
                        creatorNickname = child;
                    } else {
                        publishTime = child;
                    }
                }
            }
            logger.d("搜索页面，视频列表，遍历昵称、发布日期，RelativeLayout - LinearLayout - FrameLayout 的一级子元素");
            List<UiObject2> likeLayoutChildren = likeLayout.getChildren();
            UiObject2 likeButton = null;
            UiObject2 likeCount = null;
            for (UiObject2 child : likeLayoutChildren) {
                if (child.getClassName().equals("android.widget.ImageView")) {
                    likeButton = child;
                } else if (child.getClassName().equals("android.widget.TextView")) {
                    likeCount = child;
                }
            }
            if (creatorNickname == null || publishTime == null || likeButton == null || likeCount == null) {
                logger.e("搜索页面，视频列表，视频信息至少有一个为空，关键词 %s creatorNickname=%s, publishTime=%s, likeButton=%s, likeCount=%s",
                        keyword, creatorNickname, publishTime, likeButton, likeCount);
                continue;
            }

            // 所有完整的视频元素中，最靠近屏幕底部的视频元素的底部Y坐标
            if (videoRootLayoutBottomY < videoRootLayout.getVisibleBounds().bottom) {
                videoRootLayoutBottomY = videoRootLayout.getVisibleBounds().bottom;
                logger.i("搜索页面，视频列表，视频元素的底部Y坐标 %d", videoRootLayoutBottomY);
            }

            // 收集博主信息，标题、昵称、发布、点赞
            video.setShortTitle(videoShortTitle);
            video.setTitle(videoTitle.getText());
            creator.setNickname(creatorNickname.getText());
            video.setPublishTime(this.getPublishTimeFromText(publishTime.getText()));
            video.setLikeCount(this.getCountFromText(likeCount.getText()));

            logger.i("************ 搜索页面 ************");
            logger.i("视频标题: %s", video.getTitle().replaceAll("[\r\n]", ""));
            logger.i("博主昵称: %s", creator.getNickname());
            logger.i("发布时间: %s", video.getPublishTime());
            logger.i("点赞数量: %s", video.getLikeCount());

            // 点击视频封面，进入视频详情页
            // 点击视频标题，有较低概率触发不符预期和内容反馈弹窗
            // 点击用户头像，会进入视频详情页或直播详情页，进入直播页面后，很难退出来，会有各种阻拦弹窗
            logger.i("点击视频标题，准备进入视频详情页");
            device.click(creatorLayout.getVisibleBounds().centerX(), creatorLayout.getVisibleBounds().bottom - 2);
            sleep(SLEEP_AFTER_HTTP_REQUEST);

            // 视频详情页会自动播放视频，导致 device.wait 要等待很久才能响应，点击屏幕最右边使视频暂停
            logger.i("视频详情页，点击屏幕最右边，使视频暂停");
            device.click(deviceWidth - 1, deviceHeight / 2);

            // 视频详情页，偶尔会找不到说点什么按钮，需要重试
            for (int i = 0; i < 3; i++) {
                // 关键字 "说点什么..." "购买同款" （使用 UIAutomatorViewer.bat 查看）
                logger.i("视频详情页，查找说点什么或购买同款或直播间购买按钮，重试次数 %d", i);
                UiObject2 saySomethingView = device.findObject(By.clazz("android.widget.TextView").text("说点什么..."));
                if (saySomethingView != null) {
                    logger.i("视频详情页，找到说点什么按钮");
                    break;
                }
                UiObject2 buyTheSameView = device.findObject(By.clazz("android.widget.TextView").text("购买同款"));
                if (buyTheSameView != null) {
                    logger.i("视频详情页，找到购买同款按钮");
                    break;
                }
                UiObject2 buyInLivingView = device.findObject(By.clazz("android.widget.TextView").text("直播间购买"));
                if (buyInLivingView != null) {
                    logger.i("视频详情页，找到直播间购买按钮");
                    break;
                }

                logger.i("视频详情页，等待说点什么按钮");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("说点什么...")), REFRESH_PAGE_TIMEOUT);
                logger.i("视频详情页，等待购买同款按钮");
                boolean hasObject2 = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("购买同款")), REFRESH_PAGE_TIMEOUT);
                logger.i("视频详情页，等待直播间购买按钮");
                boolean hasObject3 = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("购买直播间")), REFRESH_PAGE_TIMEOUT);
                if (hasObject || hasObject2 || hasObject3) {
                    logger.i("视频详情页，找到说点什么或购买同款或直播间购买按钮");
                } else {
                    logger.w("视频详情页，找不到说点什么或购买同款或直播间购买按钮");
                    if (i == 2) {
                        return false;
                    }
                    continue;
                }
                break;
            }

//                logger.w("视频详情页，找不到分享按钮，可能是搜索页面，不符预期内容反馈弹窗，关键词 %s", keyword);
//
//                // 搜索页面，不符预期内容反馈弹窗
//                // 关键字 "不符预期" "内容反馈" （使用 UIAutomatorViewer.bat 查看）
//                logger.i("搜索页面，等待不符预期加载");
//                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").desc("不符预期")), REFRESH_PAGE_TIMEOUT);
//                if (!hasObject) {
//                    logger.w("搜索页面，找不到不符预期按钮，未知页面，关键词 %s", keyword);
//                }
//                logger.i("搜索页面，等待内容反馈加载");
//                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").desc("内容反馈")), REFRESH_PAGE_TIMEOUT);
//                if (!hasObject) {
//                    logger.w("搜索页面，找不到内容反馈按钮，未知页面，关键词 %s", keyword);
//                }
//
//                logger.i("直播详情页");
//
//                logger.w("视频详情页，找不到分享按钮，可能是直播详情页，关键词 %s", keyword);
//
//                // 直播详情页
//                // 关键字 "礼物" （使用 UIAutomatorViewer.bat 查看）
//                logger.i("直播详情页，等待礼物按钮加载");
//                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.Button").desc("礼物")), REFRESH_PAGE_TIMEOUT);
//                if (!hasObject) {
//                    logger.w("直播详情页，找不到礼物按钮，未知页面，关键词 %s", keyword);
//                }
//
//                logger.i("直播详情页");
//
//                // 关闭直播详情页
//                // 关键字 "关闭" （使用 UIAutomatorViewer.bat 查看）
//                logger.i("直播详情页，等待关闭按钮加载");
//                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.Button").desc("关闭")), REFRESH_PAGE_TIMEOUT);
//                if (!hasObject) {
//                    logger.w("直播详情页，找不到关闭按钮，关键词 %s", keyword);
//                }
//
//                UiObject2 closeButton = device.findObject(By.clazz("android.widget.Button").desc("关闭"));
//                if (closeButton == null) {
//                    logger.e("直播详情页，找不到关闭按钮，关键词 %s", keyword);
//                    return false;
//                }
//
//                logger.i("直播详情页，点击返回按钮");
//                closeButton.click();
//                sleep(SLEEP_AFTER_SWITCH_PAGE);
//                logger.i("返回搜索页面，视频列表");
//                continue;

            logger.i("视频详情页");

            // 收集视频信息，昵称、标题、点赞、收藏、评论
            // - ViewGroup
            //   ...
            //   - Button (分享) 定位基准
            //   ...
            //   - Button (博主)
            //     - FrameLayout
            //       - View (博主头像)
            //     - LinearLayout
            //       - TextView (博主昵称)
            //   - TextView (关注/已关注)
            //   - FrameLayout
            //     - LinearLayout
            //       - FrameLayout
            //         - TextView (视频标题)
            //   - Button
            //     - ImageView
            //     - TextView (点赞数)
            //   - Button
            //     - ImageView
            //     - TextView (收藏数)
            //   - Button
            //     - ImageView
            //     - TextView (评论数)

            // Button (分享) 定位基准
            UiObject2 shareButton = device.findObject(By.clazz("android.widget.Button").desc("分享"));
            if (shareButton == null) {
                logger.e("视频详情页，找不到分享按钮，关键词 %s", keyword);
                return false;
            }
            UiObject2 videoDetailGroup = shareButton.getParent();

            // 是否正在直播(视频列表中，点击封面进入视频详情页，点击头像进入视频详情页或直播详情)
            boolean isLive = false;

            // 使用 getChildren 遍历一级子元素
            logger.i("视频详情页，遍历博主、标题、点赞、收藏、评论，ViewGroup 的一级子元素");
            List<UiObject2> videoGroupChildren = videoDetailGroup.getChildren();
            UiObject2 creatorButtonInVideoPage = null;
            String creatorNicknameInVideoPageText = null;
            String videoTitleInVideoPageText = null;
            String likeCountInVideoPageText = null;
            String favoriteCountInVideoPageText = null;
            String commentCountInVideoPageText = null;
            for (UiObject2 child : videoGroupChildren) {
                logger.i("视频详情页，一级子元素 ViewGroup - *， class=%s", child.getClassName());

                if (child.getClassName().equals("android.widget.Button")) {
                    String desc = child.getContentDescription();
                    UiObject2 view = child.findObject(By.clazz("android.view.View"));
                    UiObject2 imageView = child.findObject(By.clazz("android.widget.ImageView"));
                    UiObject2 textView = child.findObject(By.clazz("android.widget.TextView"));
                    if (desc.contains("作者 ")) {
                        // 作者 糖果果健身塑形，直播中(尝试在小红书中搜索"，直播中"的用户，几乎没有，暂时不处理)
                        if (desc.contains("，直播中")) {
                            isLive = true;
                        }
                        if (view == null || textView == null) {
                            logger.e("视频详情页，昵称结构发生变化，需要重新适配 Button - LinearLayout - TextView，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                            continue;
                        }
                        creatorButtonInVideoPage = child;
                        creatorNicknameInVideoPageText = textView.getText();
                    } else if (desc.contains("点赞") || "已".equals(desc)) {
                        // 如果该视频已经点赞，按钮的描述是"已"
                        if (imageView == null || textView == null) {
                            logger.e("视频详情页，点赞结构发生变化，需要重新适配 Button - TextView，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                            continue;
                        }
                        likeCountInVideoPageText = textView.getText();
                    } else if (desc.contains("收藏")) {
                        if (imageView == null || textView == null) {
                            logger.e("视频详情页，收藏结构发生变化，需要重新适配 Button - TextView，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                            continue;
                        }
                        favoriteCountInVideoPageText = textView.getText();
                    } else if (desc.contains("评论")) {
                        if (imageView == null || textView == null) {
                            logger.e("视频详情页，评论结构发生变化，需要重新适配 Button - TextView，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                            continue;
                        }
                        commentCountInVideoPageText = textView.getText();
                    }
                } else if (child.getClassName().equals("android.widget.FrameLayout")) {
                    if (child.getChildren().isEmpty() || !child.getChildren().get(0).getClassName().equals("android.widget.LinearLayout")) {
                        continue;
                    }
                    UiObject2 linearLayout = child.getChildren().get(0);
                    if (linearLayout.getChildren().isEmpty() || !linearLayout.getChildren().get(0).getClassName().equals("android.widget.FrameLayout")) {
                        continue;
                    }
                    UiObject2 frameLayout = linearLayout.getChildren().get(0);
                    if (frameLayout.getChildren().isEmpty() || !frameLayout.getChildren().get(0).getClassName().equals("android.widget.TextView")) {
                        logger.e("视频详情页，标题结构发生变化，需要重新适配 FrameLayout - LinearLayout - FrameLayout - TextView，linearLayout=%s, frameLayout=%s, textView=%s，nickname=%s title=%s",
                                linearLayout, frameLayout, null, creator.getNickname(), videoShortTitle);
                        continue;
                    }
                    UiObject2 textView = frameLayout.getChildren().get(0);
                    videoTitleInVideoPageText = textView.getContentDescription();
                }
            }

            if (isLive) {
                logger.i("视频详情页，作者 %s 正在直播中，跳过，title=%s", creatorNicknameInVideoPageText, videoShortTitle);
                boolean suc = this.gotoSearchPageFromVideoDetailPage(videoShortTitle);
                if (!suc) {
                    return false;
                }
                continue;
            }

            if (creatorButtonInVideoPage == null || creatorNicknameInVideoPageText == null || videoTitleInVideoPageText == null ||
                    likeCountInVideoPageText == null || favoriteCountInVideoPageText == null || commentCountInVideoPageText == null) {
                logger.e("视频详情页，整体结构发生变化，需要重新适配 ViewGroup, creatorButtonInVideoPage=%s, creatorNicknameInVideoPageText=%s, videoTitleInVideoPageText=%s, likeCountInVideoPageText=%s, favoriteCountInVideoPageText=%s, commentCountInVideoPageText=%s，nickname=%s title=%s",
                        creatorButtonInVideoPage, creatorNicknameInVideoPageText, videoTitleInVideoPageText, likeCountInVideoPageText, favoriteCountInVideoPageText, commentCountInVideoPageText, creator.getNickname(), videoShortTitle);
                continue;
            }

            creator.setNickname(creatorNicknameInVideoPageText);
            video.setShortTitle(videoShortTitle);
            video.setTitle(videoTitleInVideoPageText);
            video.setLikeCount(this.getCountFromText(likeCountInVideoPageText));
            video.setFavoriteCount(this.getCountFromText(favoriteCountInVideoPageText));
            video.setCommentCount(this.getCountFromText(commentCountInVideoPageText));

            logger.i("************ 视频详情页 ************");
            logger.i("博主昵称: %s", creator.getNickname());
            logger.i("视频标题: %s", video.getTitle().replaceAll("[\r\n]", ""));
            logger.i("点赞数量: %s", video.getLikeCount());
            logger.i("收藏数量: %s", video.getFavoriteCount());
            logger.i("评论数量: %s", video.getCommentCount());

            logger.i("点击博主昵称，准备进入博主详情页");
            creatorButtonInVideoPage.click();
            sleep(SLEEP_AFTER_HTTP_REQUEST);

            boolean suc = collectCreatorInfo(creator.getNickname());
//            if (!suc) {
//                return false;
//            }

            suc = gotoVideoDetailFromCreatorDetailPage(videoShortTitle);
            if (!suc) {
                return false;
            }

            suc = this.gotoSearchPageFromVideoDetailPage(videoShortTitle);
            if (!suc) {
                return false;
            }
        }

        logger.i("搜索页面，视频列表，当前屏幕遍历完成");

        // 屏幕向上滑动，所有完整的视频元素中，最靠近屏幕底部的视频元素的底部滑动到"综合"文字的底部，尽量减少遍历重复元素
        logger.i("搜索页面，视频列表，计算滑动距离 %d - %d = %d", videoRootLayoutBottomY, compositeTextBottomY, videoRootLayoutBottomY - compositeTextBottomY);
        if (compositeTextBottomY > 0 && videoRootLayoutBottomY > compositeTextBottomY) {
            logger.i("搜索页面，视频列表，屏幕向上滑动距离 %d", videoRootLayoutBottomY - compositeTextBottomY);
            device.swipe(deviceWidth / 2, deviceHeight * 4 / 5, deviceWidth / 2, deviceHeight * 4 / 5 - (videoRootLayoutBottomY - compositeTextBottomY), 80);
            sleep(SLEEP_AFTER_SWIPE);

            // 继续遍历视频列表
            isContinueTraversingVideoFlag = true;
            sleep(SLEEP_AFTER_SWITCH_PAGE);
        }

        return true;
    }

    // 博主详情页，收集博主信息
    // 所有的错误返回false，不要抛出异常
    private boolean collectCreatorInfo(String nickname) {
        logger.i("collectCreatorInfo() nickname=%s", nickname);
        Boolean hasObject;

        // 博主详情页，偶尔会找不到获赞与收藏按钮，需要重试
        for (int i = 0; i < 3; i++) {
            logger.i("博主详情页，查找获赞与收藏按钮，重试次数 %d", i);
            UiObject2 likeCollectView = device.findObject(By.clazz("android.widget.TextView").text("获赞与收藏"));
            if (likeCollectView != null) {
                logger.i("博主详情页，找到获赞与收藏按钮");
                break;
            }

            // 关键字 "获赞与收藏" （使用 UIAutomatorViewer.bat 查看）
            logger.i("博主详情页，等待获赞与收藏按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("获赞与收藏")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.w("博主详情页，找不到获赞与收藏按钮");
                if (i == 2) {
                    return false;
                }
                continue;
            }
            logger.i("博主详情页");
            break;
        }

        // 博主详情页
        // 关键字 "获赞与收藏" （使用 UIAutomatorViewer.bat 查看）
        logger.i("博主详情页，查找获赞与收藏按钮");
        UiObject2 likeCollectView = device.findObject(By.clazz("android.widget.TextView").text("获赞与收藏"));
        if (likeCollectView == null) {
            logger.e("博主详情页，找不到获赞与收藏按钮，nickname=%s", nickname);
            return false;
        }
        logger.i("博主详情页");

        // 收集博主信息，昵称、小红书号、IP属地、简介、标签、关注、粉丝、获赞与收藏
        // - LinearLayout
        //   - ViewGroup
        //     - View
        //     - TextView (博主昵称)
        //     - LinearLayout
        //       - LinearLayout
        //         - TextView (小红书号：123456)
        //     - TextView (IP属地：浙江)
        //   - ViewGroup (可能不存在)
        //     - TextView (简介，可能不存在)
        //     - LinearLayout (标签组，可能不存在)
        //       - LinearLayout (标签)
        //       - LinearLayout (标签)
        //   - ViewGroup
        //     - ViewGroup
        //       - Button
        //         - TextView (关注数量)
        //         - TextView (关注)
        //       - Button
        //         - TextView (粉丝数量)
        //         - TextView (粉丝)
        //       - Button
        //         - TextView (获赞与收藏数量)
        //         - TextView (获赞与收藏) 定位基准
        //     - LinearLayout (关注、私聊按钮)
        //       - Button
        //       - ImageView

        // TextView (获赞与收藏) 定位基准
        if (!likeCollectView.getParent().getClassName().equals("android.widget.Button")) {
            logger.e("博主详情页，整体结构发生变化，需要重新适配 LinearLayout - ViewGroup - ViewGroup - Button， class=%s, nickname=%s", likeCollectView.getParent().getClassName(), nickname);
            return false;
        }
        if (!likeCollectView.getParent().getParent().getClassName().equals("android.view.ViewGroup")) {
            logger.e("博主详情页，整体结构发生变化，需要重新适配 LinearLayout - ViewGroup - ViewGroup， class=%s, nickname=%s", likeCollectView.getParent().getParent().getClassName(), nickname);
            return false;
        }
        if (!likeCollectView.getParent().getParent().getParent().getClassName().equals("android.view.ViewGroup")) {
            logger.e("博主详情页，整体结构发生变化，需要重新适配 LinearLayout - ViewGroup， class=%s, nickname=%s", likeCollectView.getParent().getParent().getParent().getClassName(), nickname);
            return false;
        }
        if (!likeCollectView.getParent().getParent().getParent().getParent().getClassName().equals("android.widget.LinearLayout")) {
            logger.e("博主详情页，整体结构发生变化，需要重新适配 LinearLayout， class=%s, nickname=%s", likeCollectView.getParent().getParent().getParent().getParent().getClassName(), nickname);
            return false;
        }

        UiObject2 creatorDetailGroup = likeCollectView.getParent().getParent().getParent().getParent();

        // 使用 getChildren 遍历一级子元素
        logger.i("博主详情页，遍历博主、简介、关注，LinearLayout 的一级子元素");
        List<UiObject2> creatorGroupChildren = creatorDetailGroup.getChildren();
        String creatorNicknameInCreatorPageText = null;
        String creatorNumberInCreatorPageText = null;
        String creatorLocationInCreatorPageText = null;
        String creatorIntroductionInCreatorPageText = null;
        StringBuilder creatorTagsInCreatorPageText = null;
        String followCountInCreatorPageText = null;
        String fansCountInCreatorPageText = null;
        String likeCollectCountInCreatorPageText = null;
        UiObject2 likeCollectCountViewInCreatorPage = null;
        for (UiObject2 child : creatorGroupChildren) {
            logger.i("博主详情页，一级子元素 LinearLayout - ViewGroup, class=%s", child.getClassName());

            if (!child.getClassName().equals("android.view.ViewGroup")) {
                logger.e("博主详情页，整体结构发生变化，需要重新适配 LinearLayout - ViewGroup, class=%s, nickname=%s", child.getClassName(), nickname);
                break;
            }
            UiObject2 view = getChildByClassName(child, "android.view.View");
            UiObject2 textView = getChildByClassName(child, "android.widget.TextView");
            UiObject2 linearLayout = getChildByClassName(child, "android.widget.LinearLayout");
            UiObject2 viewGroup = getChildByClassName(child, "android.view.ViewGroup");

            if (viewGroup != null) {
                // 判断一级子元素个数，getChildren 不包括隐藏元素，getChildCount 包括隐藏元素
                if (viewGroup.getChildren().size() != 3) {
                    logger.e("博主详情页，关注、粉丝、获赞与收藏结构发生变化，需要重新适配 LinearLayout - ViewGroup - ViewGroup - Button, button.size=%d, nickname=%s", viewGroup.getChildren().size(), nickname);
                    break;
                }
                // 关注、粉丝、获赞与收藏
                logger.i("博主详情页，遍历关注、粉丝、获赞与收藏，LinearLayout - ViewGroup - ViewGroup - Button");
                for (UiObject2 button : viewGroup.getChildren()) {
                    // 判断一级子元素个数，getChildren 不包括隐藏元素，getChildCount 包括隐藏元素
                    if (button.getChildren().size() != 2) {
                        logger.e("博主详情页，关注、粉丝、获赞与收藏结构发生变化，需要重新适配 LinearLayout - ViewGroup - ViewGroup - Button, textView.size=%d, nickname=%s", button.getChildren().size(), nickname);
                        break;
                    }
                    if (button.getContentDescription().contains("关注")) {
                        followCountInCreatorPageText = button.getContentDescription().replace("关注", "");
                    } else if (button.getContentDescription().contains("粉丝")) {
                        fansCountInCreatorPageText = button.getContentDescription().replace("粉丝", "");
                    } else if (button.getContentDescription().contains("获赞与收藏")) {
                        likeCollectCountViewInCreatorPage = button;
                        likeCollectCountInCreatorPageText = button.getContentDescription().replace("获赞与收藏", "");
                    }
                }
            } else if (view != null && textView != null && linearLayout != null) {
                List<UiObject2> textViewList = child.findObjects(By.clazz("android.widget.TextView"));
                // 昵称、小红书号、IP属地
                logger.i("博主详情页，遍历博主、小红书号、IP属地，LinearLayout - ViewGroup - View - TextView");
                for (UiObject2 item : textViewList) {
                    // 使用 findObjects() 查找的元素可能包含隐藏元素，需要过滤掉
                    if (!item.isClickable() || item.getText() == null) {
                        continue;
                    }
                    if (item.getText().contains("小红书号：")) {
                        creatorNumberInCreatorPageText = item.getText();
                    } else if (item.getText().contains("IP属地：")) {
                        creatorLocationInCreatorPageText = item.getText();
                    } else {
                        // 已实名认证的博主，这里会采集到已实名字段，过滤掉
                        if (!"已实名".equals(item.getText())) {
                            creatorNicknameInCreatorPageText = item.getText();
                        }
                    }
                }
            } else if (textView != null || linearLayout != null) {
                // 简介、标签
                if (textView == null) {
                    creatorIntroductionInCreatorPageText = "";
                } else {
                    creatorIntroductionInCreatorPageText = textView.getText();
                    // 简介可能会折叠，需要点击展开(简介文字中可能包含@另一个账号，点击简介会跳转到另一个账号的详情页)
                    try {
                        if (creatorIntroductionInCreatorPageText.contains("展开")) {
                            logger.i("博主详情页，点击简介右上角区域，展开简介");
                            // androidx.test.uiautomator.StaleObjectException
                            device.click(textView.getVisibleBounds().right - 1, textView.getVisibleBounds().top + 1);
                            sleep(SLEEP_AFTER_CLICK);
                            creatorIntroductionInCreatorPageText = textView.getText();
                        }
                    } catch (Exception e) {
                        logger.e("博主详情页，点击简介文字，展开简介失败, nickname=%s", nickname);
                    }
                }
                if (linearLayout == null) {
                    creatorTagsInCreatorPageText = new StringBuilder();
                } else {
                    logger.i("博主详情页，遍历标签");
                    try {
                        for (UiObject2 linearLayoutChild : linearLayout.getChildren()) {
                            if (!linearLayoutChild.getClassName().equals("android.widget.LinearLayout")) {
                                logger.e("博主详情页，标签结构发生变化，需要重新适配 LinearLayout - ViewGroup - LinearLayout - LinearLayout, class=%s, nickname=%s", linearLayoutChild.getClassName(), nickname);
                                continue;
                            }
                            if (linearLayoutChild.getContentDescription() == null) {
                                logger.e("博主详情页，标签结构发生变化，需要重新适配 LinearLayout - ViewGroup - LinearLayout - LinearLayout, desc=%s, nickname=%s", linearLayoutChild.getContentDescription(), nickname);
                                continue;
                            }
                            String tag = linearLayoutChild.getContentDescription();
                            // 小红书官方使用中文逗号作为性别年龄的分隔符，例如 "女，23岁" "女，"
                            tag = tag.replace("，", "|");
                            if (tag.endsWith("|")) {
                                tag = tag.substring(0, tag.length() - 1);
                            }
                            // 这里使用|作为标签的分隔符
                            if (creatorTagsInCreatorPageText == null) {
                                creatorTagsInCreatorPageText = new StringBuilder(tag);
                            } else {
                                creatorTagsInCreatorPageText.append("|").append(tag);
                            }
                        }
                    } catch (Exception e) {
                        logger.e("博主详情页，标签结构发生变化，需要重新适配 LinearLayout - ViewGroup - LinearLayout - LinearLayout, nickname=%s", nickname);
                    }
                }
            } else {
                logger.e("博主详情页，二级结构发生变化，需要重新适配 LinearLayout - ViewGroup - *, view=%s, textView=%s, linearLayout=%s, viewGroup=%s, nickname=%s", view, textView, linearLayout, viewGroup, nickname);
            }
        }
        // 简介、标签可能为空
        if (creatorIntroductionInCreatorPageText == null) {
            creatorIntroductionInCreatorPageText = "";
        }
        if (creatorTagsInCreatorPageText == null) {
            creatorTagsInCreatorPageText = new StringBuilder();
        }
        if (creatorNicknameInCreatorPageText == null || creatorNumberInCreatorPageText == null || creatorLocationInCreatorPageText == null ||
                followCountInCreatorPageText == null || fansCountInCreatorPageText == null || likeCollectCountInCreatorPageText == null) {
            logger.e("博主详情页，整体结构发生变化，需要重新适配 LinearLayout - ViewGroup, creatorNicknameInCreatorPageText=%s, creatorNumberInCreatorPageText=%s, creatorLocationInCreatorPageText=%s, creatorIntroductionInCreatorPageText=%s, creatorTagsInCreatorPageText=%s, followCountInCreatorPageText=%s, fansCountInCreatorPageText=%s, likeCollectCountInCreatorPageText=%s, nickname=%s",
                    creatorNicknameInCreatorPageText, creatorNumberInCreatorPageText, creatorLocationInCreatorPageText, creatorIntroductionInCreatorPageText, creatorTagsInCreatorPageText, followCountInCreatorPageText, fansCountInCreatorPageText, likeCollectCountInCreatorPageText, nickname);
            return false;
        }

        // 查找实名信息
        // 关键字 "已实名" （使用 UIAutomatorViewer.bat 查看）
        String realNameText = "";
        UiObject2 realNameView = creatorDetailGroup.findObject(By.clazz("android.widget.TextView").text("已实名"));
        if (realNameView != null) {
            logger.i("博主详情页，点击已实名按钮，准备打开实名弹窗， class=%s", realNameView.getClassName());
            realNameView.click();
            sleep(SLEEP_AFTER_SWITCH_PAGE);

            logger.i("博主详情页，实名弹窗，等待已实名认证按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("已实名认证")), REFRESH_PAGE_TIMEOUT);
            if (hasObject) {
                logger.i("博主详情页，实名弹窗");

                logger.i("博主详情页，实名弹窗，查找实名信息");
                List<UiObject2> textViews = device.findObjects(By.clazz("android.widget.TextView").enabled(true));
                for (UiObject2 textView : textViews) {
                    if (textView.getText() == null) {
                        continue;
                    }
                    if (textView.getText().contains("M真实姓名: ")) {
                        realNameText = textView.getText().replace("M真实姓名: ", "");

                        // 关闭实名弹窗
                        // 关键字 "知道了" （使用 UIAutomatorViewer.bat 查看）
                        logger.i("博主详情页，实名弹窗，等待知道了按钮加载");
                        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("知道了")), REFRESH_PAGE_TIMEOUT);
                        if (!hasObject) {
                            logger.e("博主详情页，实名弹窗，找不到知道了按钮, nickname=%s", nickname);
                            return false;
                        }

                        UiObject2 closeButton = device.findObject(By.clazz("android.widget.TextView").text("知道了"));
                        if (closeButton == null) {
                            logger.e("博主详情页，实名弹窗，找不到知道了按钮, nickname=%s", nickname);
                            return false;
                        }

                        logger.i("博主详情页，实名弹窗，点击返回按钮");
                        closeButton.click();
                        sleep(SLEEP_AFTER_SWITCH_PAGE);
                        logger.i("返回博主详情页");
                        break;
                    }
                }
            } else {
                logger.e("博主详情页，实名弹窗，找不到已实名认证弹窗, nickname=%s", nickname);
            }
        }

        // 查找笔记数量、点赞数量、收藏数量
        // 关键字 "当前发布笔记数" "当前获得点赞数" "当前获得收藏数" （使用 UIAutomatorViewer.bat 查看）
        String noteCountText = "";
        String likeCountText = "";
        String favoriteCountText = "";
        if (likeCollectCountViewInCreatorPage != null) {
            logger.i("博主详情页，点击获赞与收藏文字，准备打开获赞与收藏弹窗，, class=%s", likeCollectCountViewInCreatorPage.getClassName());
            likeCollectCountViewInCreatorPage.click();
            sleep(SLEEP_AFTER_SWITCH_PAGE);

            hasObject = true;
            logger.i("博主详情页，获赞与收藏弹窗，查找当前获得收藏数按钮");
            UiObject2 nowFavoriteCountView = device.findObject(By.clazz("android.widget.TextView").text("当前获得收藏数"));
            if (nowFavoriteCountView == null) {
                logger.w("博主详情页，获赞与收藏弹窗，找不到当前获得收藏数按钮");

                logger.i("博主详情页，获赞与收藏弹窗，等待当前获得收藏数按钮");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("当前获得收藏数")), REFRESH_PAGE_TIMEOUT);
                if (!hasObject) {
                    logger.e("博主详情页，获赞与收藏弹窗，找不到当前获得收藏数按钮, nickname=%s", nickname);
                }
            }

            if (nowFavoriteCountView != null && hasObject) {
                logger.i("博主详情页，获赞与收藏弹窗");

                logger.i("博主详情页，获赞与收藏弹窗，查找笔记数量");
                UiObject2 nowNoteCountView = device.findObject(By.clazz("android.widget.TextView").text("当前发布笔记数"));
                if (nowNoteCountView != null) {
                    List<UiObject2> noteCountNeighbors = nowNoteCountView.getParent().getChildren();
                    for (UiObject2 neighbor : noteCountNeighbors) {
                        if (neighbor.getClassName().equals("android.widget.TextView")) {
                            if (neighbor.getText().contains("当前发布笔记数")) {
                                continue;
                            }
                            noteCountText = neighbor.getText();
                        }
                    }
                }

                logger.i("博主详情页，获赞与收藏弹窗，查找点赞数量");
                UiObject2 nowLikeCountView = device.findObject(By.clazz("android.widget.TextView").text("当前获得点赞数"));
                if (nowLikeCountView != null) {
                    List<UiObject2> likeCountNeighbors = nowLikeCountView.getParent().getChildren();
                    for (UiObject2 neighbor : likeCountNeighbors) {
                        if (neighbor.getClassName().equals("android.widget.TextView")) {
                            if (neighbor.getText().contains("当前获得点赞数")) {
                                continue;
                            }
                            likeCountText = neighbor.getText();
                        }
                    }
                }

                logger.i("博主详情页，获赞与收藏弹窗，查找当前获得收藏数按钮");
                nowFavoriteCountView = device.findObject(By.clazz("android.widget.TextView").text("当前获得收藏数"));
                if (nowFavoriteCountView != null) {
                    List<UiObject2> favoriteCountNeighbors = nowFavoriteCountView.getParent().getChildren();
                    for (UiObject2 neighbor : favoriteCountNeighbors) {
                        if (neighbor.getClassName().equals("android.widget.TextView")) {
                            if (neighbor.getText().contains("当前获得收藏数")) {
                                continue;
                            }
                            favoriteCountText = neighbor.getText();
                        }
                    }
                }
            }

            if (nowFavoriteCountView != null && hasObject) {
                // 关闭获赞与收藏弹窗，偶尔会失败，需要重试
                for (int i = 0; i < 3; i++) {
                    // 关键字 "我知道了" （使用 UIAutomatorViewer.bat 查看）
                    logger.i("博主详情页，获赞与收藏弹窗，查找我知道了按钮，重试次数 %d", i);
                    UiObject2 closeButton = device.findObject(By.clazz("android.widget.TextView").text("我知道了"));
                    if (closeButton == null) {
                        logger.w("博主详情页，获赞与收藏弹窗，找不到我知道了按钮");

                        logger.i("博主详情页，获赞与收藏弹窗，等待我知道了按钮");
                        hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("我知道了")), REFRESH_PAGE_TIMEOUT);
                        if (!hasObject) {
                            logger.w("博主详情页，获赞与收藏弹窗，找不到我知道了按钮");
                            if (i == 2) {
                                return false;
                            }
                            continue;
                        }
                    }

                    logger.i("博主详情页，获赞与收藏弹窗，点击我知道了按钮");
                    closeButton.click();
                    sleep(SLEEP_AFTER_SWITCH_PAGE);
                    logger.i("返回博主详情页");
                    break;
                }

                // 判断博主详情页
                // 关键字 "返回" （使用 UIAutomatorViewer.bat 查看）
                logger.i("博主详情页，查找返回按钮");
                UiObject2 backButton = device.findObject(By.clazz("android.widget.ImageView").desc("返回"));
                if (backButton == null) {
                    logger.w("博主详情页，找不到返回按钮");

                    logger.i("博主详情页，等待返回按钮");
                    hasObject = device.wait(Until.hasObject(By.clazz("android.widget.ImageView").desc("返回")), REFRESH_PAGE_TIMEOUT);
                    if (!hasObject) {
                        logger.e("博主详情页，找不到返回按钮1，nickname=%s", creator.getNickname());
                        return false;
                    }
                }
                logger.i("博主详情页");
            }
        }

        long updateTime = System.currentTimeMillis() / 1000;
        creator.setNickname(creatorNicknameInCreatorPageText);
        creator.setName(realNameText);
        creator.setXiaohongshuId(creatorNumberInCreatorPageText.replace("小红书号：", ""));
        creator.setIpLocation(creatorLocationInCreatorPageText.replace("IP属地：", ""));
        creator.setIntroduction(creatorIntroductionInCreatorPageText);
        creator.setTags(creatorTagsInCreatorPageText.toString());
        creator.setNoteCount(this.getCountFromText(noteCountText));
        creator.setFollowCount(this.getCountFromText(followCountInCreatorPageText));
        creator.setFansCount(this.getCountFromText(fansCountInCreatorPageText));
        creator.setLikeCount(this.getCountFromText(likeCountText));
        creator.setFavoriteCount(this.getCountFromText(favoriteCountText));
        creator.setUpdateTime(updateTime);

        logger.i("************ 博主详情页 ************");
        logger.i("博主昵称: %s", creator.getNickname());
        logger.i("实名信息: %s", realNameText);
        logger.i("小红书号: %s", creator.getXiaohongshuId());
        logger.i("IP 属地: %s", creator.getIpLocation());
        logger.i("简   介: %s", creator.getIntroduction().replaceAll("[\r\n]", ""));
        logger.i("标   签: %s", creator.getTags());
        logger.i("关注数量: %s", creator.getFollowCount());
        logger.i("粉丝数量: %s", creator.getFansCount());
        logger.i("赞藏数量: %s", likeCollectCountInCreatorPageText);
        logger.i("笔记数量: %s", creator.getNoteCount());
        logger.i("点赞数量: %s", creator.getLikeCount());
        logger.i("收藏数量: %s", creator.getFavoriteCount());

//        // 查找博主信息是否在数据库中
//        logger.d("博主详情页，根据昵称和编号查询博主是否存在 nickname=%s, id=%d", creator.getNickname(), creator.getXiaohongshuId());
//        List<Creator> creators = this.getCreatorByNicknameAndXiaohongshuId(creator.getNickname(), creator.getXiaohongshuId());
//        if (creators.isEmpty()) {
//            creator.setCreateTime(updateTime);
//            logger.d("博主详情页，插入博主数据 nickname=%s, id=%d", creator.getNickname(), creator.getXiaohongshuId());
//            int rows = this.insertCreator(creator);
//            if (rows <= 0) {
//                return false;
//            }
//            // 重复执行，可能数据库有缓存
//            for (int i = 0; i < 20; i++) {
//                logger.d("博主详情页，根据昵称和编号查询博主是否存在 nickname=%s, id=%d", creator.getNickname(), creator.getXiaohongshuId());
//                creators = this.getCreatorByNicknameAndXiaohongshuId(creator.getNickname(), creator.getXiaohongshuId());
//                if (!creators.isEmpty()) {
//                    break;
//                }
//                sleep(20);
//            }
//            if (creators.isEmpty()) {
//                logger.e("博主详情页，数据插入成功后，查询失败，不应该出现这种情况，返回，nickname=%s，id=%s", creator.getNickname(), creator.getXiaohongshuId());
//                return true;
//            } else {
//                Creator.copy(creator, creators.get(0));
//            }
//        } else {
//            if (creators.size() > 1) {
//                logger.e("博主详情页，数据库中存在多个相同昵称和小红书号的博主，不应该出现这种情况，返回，nickname=%s，id=%s", creator.getNickname(), creator.getXiaohongshuId());
//                return true;
//            } else {
//                logger.d("博主详情页，更新博主数据 nickname=%s, id=%d", creator.getNickname(), creator.getXiaohongshuId());
//                Creator.copySimple(creators.get(0), creator);
//                int rows = this.updateCreator(creators.get(0));
//                if (rows <= 0) {
//                    return false;
//                }
//            }
//        }
//
//        video.setCreatorId(creator.getCreatorId());
//        video.setUpdateTime(updateTime);
//
//        // 查询视频信息是否在数据库中
//        logger.d("博主详情页，根据标题查询视频是否存在 title=%s", video.getShortTitle());
//        List<Video> videos = this.getVideoByTitle(video.getShortTitle(), true);
//        if (videos.isEmpty()) {
//            logger.d("博主详情页，插入视频数据 title=%s", video.getShortTitle());
//            video.setCreateTime(updateTime);
//            int rows = this.insertVideo(video);
//            if (rows > 0) {
//                // 重复执行，可能数据库有缓存
//                for (int i = 0; i < 20; i++) {
//                    logger.d("博主详情页，根据标题和编号查询视频是否存在 title=%s id=%s", video.getShortTitle(), creator.getCreatorId());
//                    videos = this.getVideoByTitleAndCreatorId(video.getShortTitle(), true, creator.getCreatorId());
//                    if (!creators.isEmpty()) {
//                        break;
//                    }
//                    sleep(20);
//                }
//                if (videos.isEmpty()) {
//                    logger.e("博主详情页，数据插入成功后，查询失败，不应该出现这种情况，返回，title=%s，creatorId=%d", video.getTitle().replaceAll("[\r\n]", ""), video.getCreatorId());
//                } else {
//                    Video.copy(video, videos.get(0));
//                }
//            }
//        } else {
//            if (videos.size() > 1) {
//                logger.e("博主详情页，数据库中存在多个相同标题的视频，不应该出现这种情况，返回，title=%s", video.getTitle().replaceAll("[\r\n]", ""));
//            } else {
//                logger.d("博主详情页，更新视频数据 title=%s id=%s", video.getShortTitle(), creator.getCreatorId());
//                Video.copySimple(videos.get(0), video);
//                int rows = this.updateVideo(videos.get(0));
//                if (rows > 0) {
//                }
//            }
//        }

        // 判断博主详情页
        // 关键字 "返回" （使用 UIAutomatorViewer.bat 查看）

        logger.i("博主详情页，查找返回按钮");
        UiObject2 backButton = device.findObject(By.clazz("android.widget.ImageView").desc("返回"));
        if (backButton == null) {
            logger.w("博主详情页，找不到返回按钮");

            logger.i("博主详情页，等待返回按钮");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.ImageView").desc("返回")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.e("博主详情页，找不到返回按钮2，nickname=%s", creator.getNickname());
                return false;
            }
        }
        logger.i("博主详情页");

        // 继续遍历视频列表
        isContinueTraversingVideoFlag = true;
        return true;
    }

    // 从博主详情页面返回视频详情页面
    private boolean gotoVideoDetailFromCreatorDetailPage(String videoShortTitle) {
        logger.i("gotoVideoDetailFromCreatorDetailPage() title=%s", videoShortTitle);
        boolean hasObject;

        // 关键字 "获赞与收藏" （使用 UIAutomatorViewer.bat 查看）
        logger.w("博主详情页，查找获赞与收藏按钮");
        UiObject2 button = device.findObject(By.clazz("android.widget.TextView").text("获赞与收藏"));
        if (button == null) {
            logger.w("博主详情页，找不到获赞与收藏");

            logger.i("博主详情页，等待获赞与收藏按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("获赞与收藏")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.e("博主详情页，找不到获赞与收藏按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                return false;
            }
        }

        // 关闭博主详情页
        // 关键字 "返回" （使用 UIAutomatorViewer.bat 查看）
        logger.i("博主详情页，查找返回按钮");
        button = device.findObject(By.clazz("android.widget.ImageView").desc("返回"));
        if (button == null) {
            logger.w("博主详情页，找不到返回按钮");

            logger.i("博主详情页，等待返回按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.ImageView").desc("返回")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.e("博主详情页，找不到返回按钮3，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                return false;
            }
        }
        logger.i("博主详情页");

        // 从博主详情页返回视频详情页，偶尔会失败，需要重试
        for (int i = 0; i < 3; i++) {
            logger.i("返回视频详情页，查找说点什么或购买同款或直播间购买按钮，重试次数 %d", i);
            UiObject2 saySomethingButton = device.findObject(By.clazz("android.widget.TextView").text("说点什么..."));
            if (saySomethingButton != null) {
                logger.i("返回视频详情页，找到说点什么按钮");
                break;
            }
            UiObject2 buyTheSameView = device.findObject(By.clazz("android.widget.TextView").text("购买同款"));
            if (buyTheSameView != null) {
                logger.i("返回视频详情页，找到购买同款按钮");
                break;
            }
            UiObject2 buyInLivingView = device.findObject(By.clazz("android.widget.TextView").text("直播间购买"));
            if (buyInLivingView != null) {
                logger.i("返回视频详情页，找到直播间购买按钮");
                break;
            }

            logger.i("博主详情页，查找返回按钮");
            UiObject2 backButton = device.findObject(By.clazz("android.widget.ImageView").desc("返回"));
            if (backButton == null) {
                logger.w("博主详情页，找不到返回按钮");

                logger.i("博主详情页，等待返回加载");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.ImageView").desc("返回")), REFRESH_PAGE_TIMEOUT);
                if (!hasObject) {
                    logger.w("博主详情页，找不到返回按钮4，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                    if (i == 2) {
                        return false;
                    }
                    continue;
                }
                logger.i("视频详情页");
            }

            logger.i("博主详情页，点击返回按钮");
            backButton.click();
            sleep(SLEEP_AFTER_SWITCH_PAGE);
            logger.i("返回视频详情页");

            logger.i("返回视频详情页，查找说点什么或购买同款或直播间购买按钮，重试次数 %d", i);
            saySomethingButton = device.findObject(By.clazz("android.widget.TextView").text("说点什么..."));
            buyTheSameView = device.findObject(By.clazz("android.widget.TextView").text("购买同款"));
            buyInLivingView = device.findObject(By.clazz("android.widget.TextView").text("直播间购买"));
            if (saySomethingButton != null || buyTheSameView != null || buyInLivingView != null) {
                logger.i("返回视频详情页，找到说点什么或购买同款或直播间购买按钮");
            } else {
                logger.i("返回视频详情页，等待说点什么按钮");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("说点什么...")), REFRESH_PAGE_TIMEOUT);
                logger.i("返回视频详情页，等待购买同款按钮");
                boolean hasObject2 = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("购买同款")), REFRESH_PAGE_TIMEOUT);
                logger.i("返回视频详情页，等待直播间购买按钮");
                boolean hasObject3 = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("直播间购买")), REFRESH_PAGE_TIMEOUT);
                if (hasObject || hasObject2 || hasObject3) {
                    logger.i("返回视频详情页，找到说点什么或购买同款或直播间购买按钮");
                } else {
                    logger.w("视频详情页，找到说点什么或购买同款或直播间购买按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                    if (i == 2) {
                        return false;
                    }
                    continue;
                }
            }

            logger.i("视频详情页");
            break;
        }

        logger.i("返回视频详情页，查找说点什么按钮");
        UiObject2 saySomethingButton = device.findObject(By.clazz("android.widget.TextView").text("说点什么..."));
        logger.i("返回视频详情页，查找购买同款按钮");
        UiObject2 buyTheSameView = device.findObject(By.clazz("android.widget.TextView").text("购买同款"));
        logger.i("返回视频详情页，查找直播间购买按钮");
        UiObject2 buyInLivingView = device.findObject(By.clazz("android.widget.TextView").text("直播间购买"));
        if (saySomethingButton == null && buyTheSameView == null && buyInLivingView == null) {
            logger.e("视频详情页，找不到说点什么或购买同款或直播间购买按钮，nickname=%s", creator.getNickname(), videoShortTitle);
            return false;
        }
        logger.i("视频详情页");
        return true;
    }

    // 从视频详情页面返回搜索页面
    private boolean gotoSearchPageFromVideoDetailPage(String videoShortTitle) {
        logger.i("gotoSearchPageFromVideoDetailPage() title=%s", videoShortTitle);
        boolean hasObject;

        // 关键字 "说点什么" （使用 UIAutomatorViewer.bat 查看）
        logger.w("视频详情页，查找说点什么按钮");
        UiObject2 button = device.findObject(By.clazz("android.widget.TextView").text("说点什么..."));
        UiObject2 buyTheSameView = device.findObject(By.clazz("android.widget.TextView").text("购买同款"));
        UiObject2 buyInLivingView = device.findObject(By.clazz("android.widget.TextView").text("直播间购买"));
        if (button != null || buyTheSameView != null || buyInLivingView != null) {
            logger.i("视频详情页，找到说点什么或购买同款或直播间购买按钮");
        } else {
            if (button == null) {
                logger.w("视频详情页，找不到说点什么按钮");

                logger.i("视频详情页，等待说点什么按钮");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("说点什么...")), REFRESH_PAGE_TIMEOUT);
                if (!hasObject) {
                    logger.e("视频详情页，找不到说点什么按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                    return false;
                }
            } else if (buyTheSameView == null) {
                logger.w("视频详情页，找不到购买同款按钮");

                logger.i("视频详情页，等待购买同款按钮");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("购买同款")), REFRESH_PAGE_TIMEOUT);
                if (!hasObject) {
                    logger.e("视频详情页，找不到购买同款按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                    return false;
                }
            } else if (buyInLivingView == null) {
                logger.w("视频详情页，找不到直播间购买按钮");

                logger.i("视频详情页，等待直播间购买按钮");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("直播间购买")), REFRESH_PAGE_TIMEOUT);
                if (!hasObject) {
                    logger.e("视频详情页，找不到直播间购买按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                    return false;
                }
            }
        }

        // 关闭视频详情页
        // 关键字 "返回" （使用 UIAutomatorViewer.bat 查看）
        logger.w("视频详情页，查找返回按钮");
        button = device.findObject(By.clazz("android.widget.Button").desc("返回"));
        if (button == null) {
            logger.w("视频详情页，找不到返回按钮");

            logger.i("视频详情页，等待返回按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.Button").desc("返回")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.e("视频详情页，找不到返回按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
                return false;
            }
        }

        // 从视频详情页返回搜索页面，偶尔会失败，需要重试
        for (int i = 0; i < 3; i++) {
            logger.i("返回搜索页面，查找搜索按钮，重试次数 %d", i);
            UiObject2 homeSearchButton = device.findObject(By.clazz("android.widget.TextView").text("搜索"));
            if (homeSearchButton != null) {
                logger.i("返回搜索页面，找到搜索按钮");
                break;
            }

            logger.i("视频详情页，查找返回按钮");
            UiObject2 backButton = device.findObject(By.clazz("android.widget.Button").desc("返回"));
            if (backButton == null) {
                logger.w("视频详情页，找不到返回按钮");

                logger.i("视频详情页，等待返回按钮加载");
                hasObject = device.wait(Until.hasObject(By.clazz("android.widget.Button").desc("返回")), REFRESH_PAGE_TIMEOUT);
                if (!hasObject) {
                    logger.w("视频详情页，找不到返回按钮");
                    if (i == 2) {
                        return false;
                    }
                    continue;
                }
            }

            logger.i("视频详情页，点击返回按钮");
            backButton.click();
            sleep(SLEEP_AFTER_SWITCH_PAGE);
            logger.i("返回搜索页面，视频列表");

            logger.i("搜索页面，视频列表，等待搜索按钮加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("搜索")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.w("搜索页面，视频列表，找不到搜索按钮，重试第 %d 次，nickname=%s title=%s", i, creator.getNickname(), videoShortTitle);
                continue;
            }

            logger.i("搜索页面，视频列表，等待综合分类加载");
            hasObject = device.wait(Until.hasObject(By.clazz("android.widget.TextView").text("综合")), REFRESH_PAGE_TIMEOUT);
            if (!hasObject) {
                logger.w("搜索页面，视频列表，找不到综合分类，重试第 %d 次，nickname=%s title=%s", i, creator.getNickname(), videoShortTitle);
                continue;
            }
            logger.i("搜索页面，视频列表");
            break;
        }

        logger.i("返回搜索页面，视频列表，查找搜索按钮");
        UiObject2 homeSearchView = device.findObject(By.clazz("android.widget.TextView").text("搜索"));
        if (homeSearchView == null) {
            logger.e("搜索页面，视频列表，找不到搜索按钮，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
            return false;
        }

        logger.i("搜索页面，视频列表，等待综合分类加载");
        UiObject2 totalButton = device.findObject(By.clazz("android.widget.TextView").text("综合"));
        if (totalButton == null) {
            logger.e("搜索页面，视频列表，找不到综合分类，nickname=%s title=%s", creator.getNickname(), videoShortTitle);
            return false;
        }

        logger.d("搜索页面，视频列表");
        return true;
    }

    private @Nullable UiObject2 getChildByClassName(@NotNull UiObject2 parent, @NotNull String className) {
        for (UiObject2 child : parent.getChildren()) {
            try {
                // androidx.test.uiautomator.StaleObjectException
                if (child.getClassName().equals(className)) {
                    return child;
                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // 通过文本获取数量
    private int getCountFromText(@NotNull String countText) {
        // 示例：12、1.2万、12万、120万、1.2亿、12亿
        if (countText.isEmpty()) {
            return 0;
        }
        try {
            if (countText.contains("万")) {
                return (int) (Float.parseFloat(countText.replace("万", "")) * 10000);
            } else if (countText.contains("亿")) {
                return (int) (Float.parseFloat(countText.replace("亿", "")) * 100000000);
            } else {
                return Integer.parseInt(countText);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 通过文本获取发布时间，格式为 YYYY-MM-DD HH:MM:SS
    private @NotNull String getPublishTimeFromText(@NotNull String publishTimeText) {
        // "xx分钟前" "xx小时前" "昨天 21:32" "xx天前" "xx-xx" "xx-xx-xx"
        if (publishTimeText.isEmpty()) {
            return "0000-00-00 00:00:00";
        }
        try {
            if (publishTimeText.contains("分钟前")) {
                int minutes = Integer.parseInt(publishTimeText.replace("分钟前", ""));
                return LocalDateTime.now().minusMinutes(minutes).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else if (publishTimeText.contains("小时前")) {
                int hours = Integer.parseInt(publishTimeText.replace("小时前", ""));
                return LocalDateTime.now().minusHours(hours).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else if (publishTimeText.contains("天前")) {
                int days = Integer.parseInt(publishTimeText.replace("天前", ""));
                return LocalDateTime.now().minusDays(days).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else if (publishTimeText.contains("昨天")) {
                String[] parts = publishTimeText.split(" ");
                return LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + parts[1] + ":00";
            } else if (publishTimeText.contains("-")) {
                String[] parts = publishTimeText.split("-");
                if (parts.length == 2) {
                    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy")) + "-" + publishTimeText + " 00:00:00";
                } else if (parts.length == 3) {
                    return publishTimeText + " 00:00:00";
                } else {
                    return "0000-00-00 00:00:00";
                }
            } else {
                return publishTimeText;
            }
        } catch (NumberFormatException e) {
            return "0000-00-00 00:00:00";
        }
    }

    // 通过标题获取视频信息
    private @Nullable List<Video> getVideoByTitle(@NotNull String title, boolean isShortTitle) {
        logger.i("getVideoByTitle(%s, %b)", title, isShortTitle);
        List<Video> videos = new ArrayList<>();
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "SELECT * FROM video WHERE title = ?";
                if (isShortTitle) {
                    sql = "SELECT * FROM video WHERE short_title = ?";
                }
                List<Map<String, Object>> results = database.executeQuery(sql, title);

                for (Map<String, Object> row : results) {
                    try {
                        Video x = new Video();
                        x.setVideoId((Long) row.get("video_id"));
                        x.setShortTitle((String) row.get("short_title"));
                        x.setTitle((String) row.get("title"));
                        x.setCreatorId((Long) row.get("creator_id"));
                        x.setPublishTime(row.get("publish_time").toString());
                        x.setLikeCount((Long) row.get("like_count"));
                        x.setFavoriteCount((Long) row.get("favorite_count"));
                        x.setCommentCount((Long) row.get("comment_count"));
                        x.setCreateTime((Long) row.get("create_time"));
                        x.setUpdateTime((Long) row.get("update_time"));
                        x.setDeleteTime((Long) row.get("delete_time"));
                        x.setRemark((String) row.get("remark"));
                        videos.add(x);
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        logger.e("查询视频信息失败，若干数值字段为空，title=%s", title.replaceAll("[\r\n]", ""));
                    }
                }

                if (videos.isEmpty()) {
                    logger.w("查询视频信息失败，title=%s", title.replaceAll("[\r\n]", ""));
                    logger.w("SELECT * FROM video WHERE title = '%s'", title.replaceAll("[\r\n]", ""));
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("查询视频信息失败，查询超时，title=%s", title.replaceAll("[\r\n]", ""));
        }
        return videos;
    }

    // 通过标题和博主编号获取视频信息
    private @NotNull List<Video> getVideoByTitleAndCreatorId(@NotNull String title, boolean isShortTitle, long creatorId) {
        logger.i("getVideoByTitleAndCreatorId(%s, %b, %s)", title.replaceAll("[\r\n]", ""), isShortTitle, creatorId);
        List<Video> videos = new ArrayList<>();
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "SELECT * FROM video WHERE title = ? AND creator_id = ?";
                if (isShortTitle) {
                    sql = "SELECT * FROM video WHERE short_title = ? AND creator_id = ?";
                }
                List<Map<String, Object>> results = database.executeQuery(sql, title, creatorId);

                for (Map<String, Object> row : results) {
                    try {
                        Video x = new Video();
                        x.setVideoId((Long) row.get("video_id"));
                        x.setShortTitle((String) row.get("short_title"));
                        x.setTitle((String) row.get("title"));
                        x.setCreatorId((Long) row.get("creator_id"));
                        x.setPublishTime(row.get("publish_time").toString());
                        x.setLikeCount((Long) row.get("like_count"));
                        x.setFavoriteCount((Long) row.get("favorite_count"));
                        x.setCommentCount((Long) row.get("comment_count"));
                        x.setCreateTime((Long) row.get("create_time"));
                        x.setUpdateTime((Long) row.get("update_time"));
                        x.setDeleteTime((Long) row.get("delete_time"));
                        x.setRemark((String) row.get("remark"));
                        videos.add(x);
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        logger.e("查询视频信息失败，若干数值字段为空，title=%s creatorId=%d", title.replaceAll("[\r\n]", ""), creatorId);
                    }
                }

                if (videos.isEmpty()) {
                    logger.w("查询视频信息失败，title=%s creatorId=%s", title.replaceAll("[\r\n]", ""), creatorId);
                    logger.w("SELECT * FROM video WHERE title = '%s' AND creator_id = %d", title.replaceAll("[\r\n]", ""), creatorId);
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("查询视频信息失败，查询超时，title=%s creatorId=%s", title.replaceAll("[\r\n]", ""), creatorId);
        }
        return videos;
    }

    // 写入视频信息
    private int insertVideo(@NotNull Video v) {
        logger.i("insertVideo(%s)", v.getTitle().replaceAll("[\r\n]", ""));
        final int[] affectedRows = {0};
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "INSERT INTO video (short_title, title, creator_id, publish_time, like_count, favorite_count, comment_count, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                affectedRows[0] = database.executeInsert(sql, v.getShortTitle(), v.getTitle(), v.getCreatorId(), v.getPublishTime(), v.getLikeCount(), v.getFavoriteCount(), v.getCommentCount(), v.getCreateTime(), v.getUpdateTime());

                if (affectedRows[0] <= 0) {
                    logger.e("写入视频信息失败，title=%s", v.getTitle().replaceAll("[\r\n]", ""));
                    logger.e("INSERT INTO video (short_title, title, creator_id, publish_time, like_count, favorite_count, comment_count, create_time, update_time) VALUES ('%s', '%s', %d, '%s', %d, %d, %d, %d, %d)",
                            v.getShortTitle(), v.getTitle(), v.getCreatorId(), v.getPublishTime(), v.getLikeCount(), v.getFavoriteCount(), v.getCommentCount(), v.getCreateTime(), v.getUpdateTime());
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("写入视频信息失败，查询超时，title=%s", v.getTitle().replaceAll("[\r\n]", ""));
        }
        return affectedRows[0];
    }

    // 根据编号更新视频信息
    private int updateVideo(@NotNull Video v) {
        logger.i("updateVideo(%d)", v.getVideoId());
        final int[] affectedRows = {0};
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "UPDATE video SET title = ?, creator_id = ?, publish_time = ?, like_count = ?, favorite_count = ?, comment_count = ?, create_time = ?, update_time = ?, delete_time = ?, remark = ? WHERE video_id = ?";
                affectedRows[0] = database.executeUpdate(sql, v.getTitle(), v.getCreatorId(), v.getPublishTime(), v.getLikeCount(), v.getFavoriteCount(), v.getCommentCount(), v.getCreateTime(), v.getUpdateTime(), v.getDeleteTime(), v.getRemark(), v.getVideoId());

                if (affectedRows[0] <= 0) {
                    logger.e("更新视频信息失败，title=%s", v.getTitle().replaceAll("[\r\n]", ""));
                    logger.e("UPDATE video SET title = '%s', creator_id = %d, publish_time = '%s', like_count = %d, favorite_count = %d, comment_count = %d, create_time = %d, update_time = %d, delete_time = %d, remark = '%s' WHERE video_id = %d",
                            v.getTitle(), v.getCreatorId(), v.getPublishTime(), v.getLikeCount(), v.getFavoriteCount(), v.getCommentCount(), v.getCreateTime(), v.getUpdateTime(), v.getDeleteTime(), v.getRemark(), v.getVideoId());
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("更新视频信息失败，查询超时，title=%s", v.getTitle().replaceAll("[\r\n]", ""));
        }
        return affectedRows[0];
    }

    // 通过昵称获取博主信息
    private @NotNull List<Creator> getCreatorByNickname(@NotNull String nickname) {
        logger.i("getCreatorByNickname(%s)", nickname);
        List<Creator> creators = new ArrayList<>();
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "SELECT * FROM creator WHERE nickname = ?";
                List<Map<String, Object>> results = database.executeQuery(sql, nickname);

                for (Map<String, Object> row : results) {
                    try {
                        Creator x = new Creator();
                        x.setCreatorId((Long) row.get("creator_id"));
                        x.setNickname((String) row.get("nickname"));
                        x.setName((String) row.get("name"));
                        x.setPhone((String) row.get("phone"));
                        x.setEmail((String) row.get("email"));
                        x.setXiaohongshuId((String) row.get("xiaohongshu_id"));
                        x.setIpLocation((String) row.get("ip_location"));
                        x.setIntroduction((String) row.get("introduction"));
                        x.setTags((String) row.get("tags"));
                        x.setNoteCount((Long) row.get("note_count"));
                        x.setFollowCount((Long) row.get("follow_count"));
                        x.setFansCount((Long) row.get("fans_count"));
                        x.setLikeCount((Long) row.get("like_count"));
                        x.setFavoriteCount((Long) row.get("favorite_count"));
                        x.setCreateTime((Long) row.get("create_time"));
                        x.setUpdateTime((Long) row.get("update_time"));
                        x.setDeleteTime((Long) row.get("delete_time"));
                        x.setRemark((String) row.get("remark"));
                        creators.add(x);
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        logger.e("查询博主信息失败，若干数值字段为空，nickname=%s", nickname);
                    }
                }

                if (creators.isEmpty()) {
                    logger.w("查询博主信息失败，nickname=%s", nickname);
                    logger.w("SELECT * FROM creator WHERE nickname = '%s'", nickname);
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("查询博主信息失败，查询超时，nickname=%s", nickname);
        }
        return creators;
    }

    // 通过昵称和小红书号获取博主信息
    private @NotNull List<Creator> getCreatorByNicknameAndXiaohongshuId(@NotNull String nickname, @NotNull String xiaohongshuId) {
        logger.i("getCreatorByNicknameAndXiaohongshuId(%s, %s)", nickname, xiaohongshuId);
        List<Creator> creators = new ArrayList<>();
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "SELECT * FROM creator WHERE nickname = ? AND xiaohongshu_id = ?";
                List<Map<String, Object>> results = database.executeQuery(sql, nickname, xiaohongshuId);

                for (Map<String, Object> row : results) {
                    try {
                        Creator x = new Creator();
                        x.setCreatorId((Long) row.get("creator_id"));
                        x.setNickname((String) row.get("nickname"));
                        x.setName((String) row.get("name"));
                        x.setPhone((String) row.get("phone"));
                        x.setEmail((String) row.get("email"));
                        x.setXiaohongshuId((String) row.get("xiaohongshu_id"));
                        x.setIpLocation((String) row.get("ip_location"));
                        x.setIntroduction((String) row.get("introduction"));
                        x.setTags((String) row.get("tags"));
                        x.setNoteCount((Long) row.get("note_count"));
                        x.setFollowCount((Long) row.get("follow_count"));
                        x.setFansCount((Long) row.get("fans_count"));
                        x.setLikeCount((Long) row.get("like_count"));
                        x.setFavoriteCount((Long) row.get("favorite_count"));
                        x.setCreateTime((Long) row.get("create_time"));
                        x.setUpdateTime((Long) row.get("update_time"));
                        x.setDeleteTime((Long) row.get("delete_time"));
                        x.setRemark((String) row.get("remark"));
                        creators.add(x);
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        logger.e("查询博主信息失败，若干数值字段为空，nickname=%s xiaohongshuId=%s", nickname, xiaohongshuId);
                    }
                }

                if (creators.isEmpty()) {
                    logger.w("查询博主信息失败，nickname=%s xiaohongshuId=%s", nickname, xiaohongshuId);
                    logger.w("SELECT * FROM creator WHERE nickname = '%s' AND xiaohongshu_id = '%s'", nickname, xiaohongshuId);
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("查询博主信息失败，查询超时，nickname=%s xiaohongshuId=%s", nickname, xiaohongshuId);
        }
        return creators;
    }

    // 写入博主信息
    private int insertCreator(@NotNull Creator c) {
        logger.i("insertCreator(%s)", c.getNickname());
        final int[] affectedRows = {0};
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "INSERT INTO creator (nickname, name, phone, email, xiaohongshu_id, ip_location, introduction, tags, note_count, follow_count, fans_count, like_count, favorite_count, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                affectedRows[0] = database.executeInsert(sql, c.getNickname(), c.getName(), c.getPhone(), c.getEmail(), c.getXiaohongshuId(), c.getIpLocation(), c.getIntroduction(), c.getTags(), c.getNoteCount(), c.getFollowCount(), c.getFansCount(), c.getLikeCount(), c.getFavoriteCount(), c.getCreateTime(), c.getUpdateTime());

                if (affectedRows[0] <= 0) {
                    logger.e("写入博主信息失败，nickname=%s xiaohongshuId=%s", c.getNickname(), c.getXiaohongshuId());
                    logger.e("INSERT INTO creator (nickname, name, phone, email, xiaohongshu_id, ip_location, introduction, tags, note_count, follow_count, fans_count, like_count, favorite_count, create_time, update_time) VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', %d, %d, %d, %d, %d, %d, %d)", c.getNickname(), c.getName(), c.getPhone(), c.getEmail(), c.getXiaohongshuId(), c.getIpLocation(), c.getIntroduction(), c.getTags(), c.getNoteCount(), c.getFollowCount(), c.getFansCount(), c.getLikeCount(), c.getFavoriteCount(), c.getCreateTime(), c.getUpdateTime());
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("写入博主信息失败，查询超时，nickname=%s xiaohongshuId=%s", c.getNickname(), c.getXiaohongshuId());
        }
        return affectedRows[0];
    }

    // 根据编号更新博主信息
    private int updateCreator(@NotNull Creator c) {
        logger.i("updateCreator(%d)", c.getCreatorId());
        final int[] affectedRows = {0};
        final int[] flag = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sql = "UPDATE creator SET nickname = ?, name = ?, phone = ?, email = ?, xiaohongshu_id = ?, ip_location = ?, introduction = ?, tags = ?, note_count = ?, follow_count = ?, fans_count = ?, like_count = ?, favorite_count = ?, create_time = ?, update_time = ?, delete_time = ?, remark = ? WHERE creator_id = ?";
                affectedRows[0] = database.executeUpdate(sql, c.getNickname(), c.getName(), c.getPhone(), c.getEmail(), c.getXiaohongshuId(), c.getIpLocation(), c.getIntroduction(), c.getTags(), c.getNoteCount(), c.getFollowCount(), c.getFansCount(), c.getLikeCount(), c.getFavoriteCount(), c.getCreateTime(), c.getUpdateTime(), c.getDeleteTime(), c.getRemark(), c.getCreatorId());

                if (affectedRows[0] <= 0) {
                    logger.e("更新博主信息失败，nickname=%s xiaohongshuId=%s", c.getNickname(), c.getXiaohongshuId());
                    logger.e("UPDATE creator SET nickname = '%s', name = '%s', phone = '%s', email = '%s', xiaohongshu_id = '%s', ip_location = '%s', introduction = '%s', tags = '%s', note_count = %d, follow_count = %d, fans_count = %d, like_count = %d, favorite_count = %d, create_time = %d, update_time = %d, delete_time = %d, remark = '%s' WHERE creator_id = %d", c.getNickname(), c.getName(), c.getPhone(), c.getEmail(), c.getXiaohongshuId(), c.getIpLocation(), c.getIntroduction(), c.getTags(), c.getNoteCount(), c.getFollowCount(), c.getFansCount(), c.getLikeCount(), c.getFavoriteCount(), c.getCreateTime(), c.getUpdateTime(), c.getDeleteTime(), c.getRemark(), c.getCreatorId());
                }
                flag[0] = 1;
            }
        }).start();
        // 循环等待DATABASE_QUERY_TIMEOUT毫秒
        for (int i = 0; i < DATABASE_QUERY_TIMEOUT; i++) {
            if (flag[0] == 0) {
                sleep(1);
            } else {
                break;
            }
        }
        if (flag[0] == 0) {
            logger.e("更新博主信息失败，查询超时，nickname=%s xiaohongshuId=%s", c.getNickname(), c.getXiaohongshuId());
        }
        return affectedRows[0];
    }

}
