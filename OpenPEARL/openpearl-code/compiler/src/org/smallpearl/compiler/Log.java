package org.smallpearl.compiler;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Log {

    static public final int LEVEL_NONE = 0;
    static public final int LEVEL_INFO = 1;
    static public final int LEVEL_WARN = 2;
    static public final int LEVEL_DEBUG = 3;
    static public final int LEVEL_ERROR = 4;

    static private int level = LEVEL_INFO;

    static public boolean ERROR = level >= LEVEL_ERROR;
    static public boolean WARN = level >= LEVEL_WARN;
    static public boolean INFO = level >= LEVEL_INFO;
    static public boolean DEBUG = level >= LEVEL_DEBUG;

    static public void set (int level) {
        Log.level = level;
        INFO = level >= LEVEL_INFO;
        WARN = level >= LEVEL_WARN;
        DEBUG = level >= LEVEL_DEBUG;
        ERROR = level >= LEVEL_ERROR;
    }

    static public void NONE () {
        set(LEVEL_NONE);
    }

    static public void ERROR () {
        set(LEVEL_ERROR);
    }

    static public void WARN () {
        set(LEVEL_WARN);
    }

    static public void INFO () {
        set(LEVEL_INFO);
    }

    static public void DEBUG () {
        set(LEVEL_DEBUG);
    }

    static public void setLogger (Logger logger) {
        Log.logger = logger;
    }

    static private Logger logger = new Logger();

    static public void error (String message, Throwable ex) {
        if (ERROR) logger.log(LEVEL_ERROR, null, message, ex);
    }

    static public void error (String category, String message, Throwable ex) {
        if (ERROR) logger.log(LEVEL_ERROR, category, message, ex);
    }

    static public void error (String message) {
        if (ERROR) logger.log(LEVEL_ERROR, null, message, null);
    }

    static public void error (String category, String message) {
        if (ERROR) logger.log(LEVEL_ERROR, category, message, null);
    }

    static public void warn (String message, Throwable ex) {
        if (WARN) logger.log(LEVEL_WARN, null, message, ex);
    }

    static public void warn (String category, String message, Throwable ex) {
        if (WARN) logger.log(LEVEL_WARN, category, message, ex);
    }

    static public void warn (String message) {
        if (WARN) logger.log(LEVEL_WARN, null, message, null);
    }

    static public void warn (String category, String message) {
        if (WARN) logger.log(LEVEL_WARN, category, message, null);
    }

    static public void info (String message, Throwable ex) {
        if (INFO) logger.log(LEVEL_INFO, null, message, ex);
    }

    static public void info (String category, String message, Throwable ex) {
        if (INFO) logger.log(LEVEL_INFO, category, message, ex);
    }

    static public void info (String message) {
        if (INFO) logger.log(LEVEL_INFO, null, message, null);
    }

    static public void info (String category, String message) {
        if (INFO) logger.log(LEVEL_INFO, category, message, null);
    }

    static public void debug (String message, Throwable ex) {
        if (DEBUG) logger.log(LEVEL_DEBUG, null, message, ex);
    }

    static public void debug (String category, String message, Throwable ex) {
        if (DEBUG) logger.log(LEVEL_DEBUG, category, message, ex);
    }

    static public void debug (String message) {
        if (DEBUG) logger.log(LEVEL_DEBUG, null, message, null);
    }

    static public void debug (String category, String message) {
        if (DEBUG) logger.log(LEVEL_DEBUG, category, message, null);
    }

    private Log () {
    }

    static public class Logger {
        private String m_filename = "prl.log";

        public void setLogFilename( String filename) {
            m_filename = filename;
            File file = new File(m_filename);
            String fileExtension = CommonUtils.getFileExtension(file).toLowerCase();

            if ( fileExtension.equals(".log")) {
                file.delete();
            }
        }

        public void log (int level, String category, String message, Throwable ex) {
            StringBuilder builder = new StringBuilder(256);

            String timeStamp = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss|").format(Calendar.getInstance().getTime());
            builder.append(timeStamp);

            switch (level) {
                case LEVEL_ERROR:
                    builder.append("ERROR|");
                    break;
                case LEVEL_WARN:
                    builder.append(" WARN|");
                    break;
                case LEVEL_INFO:
                    builder.append(" INFO|");
                    break;
                case LEVEL_DEBUG:
                    builder.append("DEBUG|");
                    break;
            }

            if (category != null) {
                builder.append('[');
                builder.append(category);
                builder.append("] ");
            }

            builder.append(message);

            if (ex != null) {
                StringWriter writer = new StringWriter(256);
                ex.printStackTrace(new PrintWriter(writer));
                builder.append('\n');
                builder.append(writer.toString().trim());
            }

            print(builder.toString());
        }

        protected void print (String message) {
            try(FileWriter fw = new FileWriter(m_filename, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
            {
                out.println(message);
            } catch (IOException e) {
                //exception handling left as an exercise for the reader
            }
        }
    }
}
