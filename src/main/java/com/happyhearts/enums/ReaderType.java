package com.happyhearts.enums;

public enum ReaderType {
    ENTRY,
    EXIT;

    public ScanType toScanType() {
        return ScanType.valueOf(name());
    }
}
