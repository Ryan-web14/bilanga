package com.sni.bilanga.utils.constant;


public class PaginationConstant {

    // Pagination defaults
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";

    // Max page size to prevent DOS attacks
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationConstant() {};
}
