package com.example.marketing.user.util;
public class UserUtil {
private UserUtil() {}
    private static final String SIZE_MIN_1_MAX_255_MESSAGE = " should contain minimum 1 and maximum of 255 characters";
    public static final String USERNAME_MESSAGE = "Username" + SIZE_MIN_1_MAX_255_MESSAGE;
    public static final String FIRST_NAME_MESSAGE = "First name" + SIZE_MIN_1_MAX_255_MESSAGE;
    public static final String LAST_NAME_MESSAGE = "Last name" + SIZE_MIN_1_MAX_255_MESSAGE;
    public static final String EMAIL_MESSAGE = "Email" + SIZE_MIN_1_MAX_255_MESSAGE;
    public static final String ADDRESS_MESSAGE = "Address" + SIZE_MIN_1_MAX_255_MESSAGE;
}
