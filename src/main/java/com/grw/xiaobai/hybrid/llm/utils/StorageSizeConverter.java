package com.grw.xiaobai.hybrid.llm.utils;

public class StorageSizeConverter {
    /**
     * 存储单位的枚举定义
     */
    public enum StorageUnit {
        BYTE("B", 1L),
        KILOBYTE("KB", 1024L),
        MEGABYTE("MB", 1024L * 1024L),
        GIGABYTE("GB", 1024L * 1024L * 1024L),
        TERABYTE("TB", 1024L * 1024L * 1024L * 1024L);

        private final String unitName;
        private final long bytes;

        StorageUnit(String unitName, long bytes) {
            this.unitName = unitName;
            this.bytes = bytes;
        }

        public String getUnitName() {
            return unitName;
        }

        public long getBytes() {
            return bytes;
        }
    }

    /**
     * 将字节大小转换为最适合的人类可读格式
     *
     * @param sizeInBytes 字节大小
     * @return 格式化后的字符串，如 "1.23 MB"
     */
    public static String formatSize(long sizeInBytes) {
        return formatSize(sizeInBytes, 2); // 默认保留两位小数
    }

    /**
     * 将字节大小转换为最适合的人类可读格式（支持自定义小数位数）
     *
     * @param sizeInBytes   字节大小
     * @param decimalPlaces 要保留的小数位数 (1-3)
     * @return 格式化后的字符串，如 "1.2 MB" 或 "1.234 TB"
     */
    public static String formatSize(long sizeInBytes, int decimalPlaces) {
        if (sizeInBytes < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }

        // 验证小数位数参数
        if (decimalPlaces < 1 || decimalPlaces > 3) {
            throw new IllegalArgumentException("Decimal places must be between 1 and 3");
        }

        if (sizeInBytes == 0) {
            return "0 B";
        }

        // 找到最适合的单位
        StorageUnit[] units = StorageUnit.values();
        int unitIndex = 0;

        while (unitIndex < units.length - 1 && sizeInBytes >= units[unitIndex + 1].getBytes()) {
            unitIndex++;
        }

        double size = (double) sizeInBytes / units[unitIndex].getBytes();

        // 根据要求的小数位数格式化输出
        String formatString;
        switch (decimalPlaces) {
            case 1:
                formatString = "%.1f %s";
                break;
            case 2:
                formatString = "%.2f %s";
                break;
            case 3:
                formatString = "%.3f %s";
                break;
            default:
                // 理论上不会执行到这里，因为前面已经验证过
                formatString = "%.2f %s";
        }

        return String.format(formatString, size, units[unitIndex].getUnitName());
    }

}
