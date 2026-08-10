package com.transiva.app;

/** Pure validation rules shared by UI and local unit tests. */
public final class MerchantSecurityRules {
    private MerchantSecurityRules() {}
    public static boolean isUsernameValid(String value) {
        return value != null && value.matches("^[a-z0-9._-]{3,32}$");
    }
    public static boolean isStrongPassword(String value) {
        return value != null && value.length() >= 8 && value.length() <= 72
                && value.matches(".*[A-Za-z].*") && value.matches(".*\\d.*");
    }
    public static boolean isPinValid(String value) {
        return value != null && value.matches("^\\d{6}$");
    }
}
