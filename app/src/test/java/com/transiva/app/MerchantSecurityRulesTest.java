package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class MerchantSecurityRulesTest {
    @Test public void usernamesAreValidated() {
        assertTrue(MerchantSecurityRules.isUsernameValid("merchant.01"));
        assertFalse(MerchantSecurityRules.isUsernameValid("AB"));
        assertFalse(MerchantSecurityRules.isUsernameValid("merchant space"));
    }
    @Test public void passwordsNeedLetterNumberAndLength() {
        assertTrue(MerchantSecurityRules.isStrongPassword("Transiva2026"));
        assertFalse(MerchantSecurityRules.isStrongPassword("12345678"));
        assertFalse(MerchantSecurityRules.isStrongPassword("password"));
        assertFalse(MerchantSecurityRules.isStrongPassword("a1"));
    }
    @Test public void pinMustBeExactlySixDigits() {
        assertTrue(MerchantSecurityRules.isPinValid("123456"));
        assertFalse(MerchantSecurityRules.isPinValid("12345"));
        assertFalse(MerchantSecurityRules.isPinValid("12a456"));
    }
}
