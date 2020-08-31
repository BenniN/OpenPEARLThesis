package org.smallpearl.compiler;

import java.io.File;
import java.text.NumberFormat;

public class SystemInformation {
    private Runtime runtime = Runtime.getRuntime();

    public String Info() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.OSInfo());
        sb.append(this.MemInfo());
        sb.append(this.DiskInfo());
        return sb.toString();
    }

    public String OSname() {
        return System.getProperty("os.name");
    }

    public String OSversion() {
        return System.getProperty("os.version");
    }

    public String OsArch() {
        return System.getProperty("os.arch");
    }

    public long totalMem() {
        return Runtime.getRuntime().totalMemory();
    }

    public long usedMem() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public String MemInfo() {
        NumberFormat format = NumberFormat.getInstance();
        StringBuilder sb = new StringBuilder();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        sb.append("Used memory         : ");
        sb.append(format.format(usedMemory) + " bytes");
        sb.append("\n");
        sb.append("Free memory         : ");
        sb.append(format.format(freeMemory) + " bytes");
        sb.append("\n");
        sb.append("Allocated memory    : ");
        sb.append(format.format(allocatedMemory) + " bytes");
        sb.append("\n");
        sb.append("Max memory          : ");
        sb.append(format.format(maxMemory) + " bytes");
        sb.append("\n");
        sb.append("Total free memory   : ");
        sb.append(format.format(freeMemory + (maxMemory - allocatedMemory)) + " bytes");
        sb.append("\n");

        return sb.toString();
    }

    public String OSInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append("OS                  : ");
        sb.append(this.OSname());
        sb.append("\n");
        sb.append("Version             : ");
        sb.append(this.OSversion() + " (" + this.OsArch() + ")");
        sb.append("\n");
        sb.append("Available cpu cores : ");
        sb.append(runtime.availableProcessors());
        sb.append("\n");

        return sb.toString();
    }

    public String DiskInfo() {
        File[] roots = File.listRoots();
        StringBuilder sb = new StringBuilder();

        for (File root : roots) {
            sb.append("File system root    : ");
            sb.append(root.getAbsolutePath());
            sb.append("\n");
            sb.append("    Total space (bytes) : ");
            sb.append(root.getTotalSpace() + " bytes");
            sb.append("\n");
            sb.append("    Free space (bytes)  : ");
            sb.append(root.getFreeSpace() + " bytes");
            sb.append("\n");
            sb.append("    Usable space (bytes): ");
            sb.append(root.getUsableSpace() + " bytes");
            sb.append("\n");
        }
        return sb.toString();
    }

}
