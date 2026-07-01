package com.happyhearts.enums;

public enum NotificationType {
    DAILY_REPORT,
    ABSENCE_ALERT,
    LATE_ALERT,
    ATTENDANCE_CHECK_IN,
    ATTENDANCE_CHECK_OUT,
    WELCOME,
    RFID_ASSIGNED,
    LOGIN_OTP,
    WELCOME_USER,
    PASSWORD_FIRST_SETUP,
    PASSWORD_RESET,
    /** Branch lead / second teacher notified when a new staff member is added. */
    NEW_EMPLOYEE_LEADER_NOTICE,
    ANNOUNCEMENT,
    FEEDBACK,
    INTERNAL_MESSAGE,
    GRACE_PERIOD_APPROVED,
    ATTENDANCE_EXCUSE_GRANTED,
    EXPLANATION_REQUEST,
    /** Staff email from Messages → Emails tab (real SMTP to employee mailbox). */
    STAFF_EMAIL
}
