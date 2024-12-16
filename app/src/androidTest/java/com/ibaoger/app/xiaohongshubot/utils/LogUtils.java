package com.ibaoger.app.xiaohongshubot.utils;

import android.content.Context;
import android.os.Environment;

import com.elvishew.xlog.BuildConfig;
import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.Flattener2;
import com.elvishew.xlog.internal.Platform;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator;
import com.elvishew.xlog.printer.file.writer.Writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// 日志工具类
public class LogUtils {
    private static final String TAG = "Utils";
    private static boolean isLogInit = false;

    // 自定义文件名生成器（由于权限问题，每次启动测试只能使用新文件名，否则无法写入文件内容）
    static class CustomFileNameGenerator implements FileNameGenerator {
        @Override
        public boolean isFileNameChangeable() {
            return true;
        }

        @Override
        public String generateFileName(int logLevel, long timestamp) {
            return new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(new Date()) + ".txt";
            //return new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(new Date()) + "_" + timestamp + ".txt";
        }
    }

    // 自定义日志格式
    static class CustomFlattener implements Flattener2 {
        private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

        @Override
        public CharSequence flatten(long timeMillis, int logLevel, String tag, String message) {
            String time = dateFormat.format(new Date());
            return time + " - " + logLevelToString(logLevel) + " - " + tag + " - " + message;
        }

        private String logLevelToString(int logLevel) {
            switch (logLevel) {
                case LogLevel.VERBOSE:
                    return "V";
                case LogLevel.DEBUG:
                    return "D";
                case LogLevel.INFO:
                    return "I";
                case LogLevel.WARN:
                    return "W";
                case LogLevel.ERROR:
                    return "E";
                default:
                    return "UNKNOWN";
            }
        }
    }

    // 自定义文件写入器，方便调试 (com.elvishew.xlog.printer.file.writer.SimpleWriter)
    static class CustomWriter extends Writer {

        private String logFileName;

        private File logFile;

        private BufferedWriter bufferedWriter;

        @Override
        public boolean open(File file) {
            logFileName = file.getName();
            logFile = file;

            boolean isNewFile = false;

            // Create log file if not exists.
            if (!logFile.exists()) {
                try {
                    File parent = logFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    logFile.createNewFile();
                    isNewFile = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    close();
                    return false;
                }
            }

            // Create buffered writer.
            try {
                bufferedWriter = new BufferedWriter(new FileWriter(logFile, true));
                if (isNewFile) {
                    onNewFileCreated(logFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
                close();
                return false;
            }
            return true;
        }

        @Override
        public boolean isOpened() {
            return bufferedWriter != null && logFile.exists();
        }

        @Override
        public File getOpenedFile() {
            return logFile;
        }

        @Override
        public String getOpenedFileName() {
            return logFileName;
        }

        public void onNewFileCreated(File file) {
        }

        @Override
        public void appendLog(String log) {
            try {
                bufferedWriter.write(log);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (Exception e) {
                Platform.get().warn("append log failed: " + e.getMessage());
            }
        }

        @Override
        public boolean close() {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            bufferedWriter = null;
            logFileName = null;
            logFile = null;
            return true;
        }
    }

    public static boolean initLog(Context context) {
        if (isLogInit) {
            return true;
        }
        String logFolderPath = getLogFolderPath();
        LogConfiguration config = new LogConfiguration.Builder()
                .logLevel(BuildConfig.DEBUG ? LogLevel.ALL : LogLevel.INFO)
                .tag("XHS")
                .build();
        XLog.init(config, new AndroidPrinter(), new FilePrinter.Builder(logFolderPath)
                .fileNameGenerator(new CustomFileNameGenerator())
                .flattener(new CustomFlattener())
                .writer(new CustomWriter())
                .build());
        isLogInit = true;
        return true;
    }

    public static String getLogFolderPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/AiBot/Xiaohongshu/";
    }
}
