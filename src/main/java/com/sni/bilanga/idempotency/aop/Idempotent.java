package com.sni.bilanga.idempotency.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String operation();
    int requestBodyArgIndex() default 0;
    String keyHeader() default "Idempotency-Key";
    boolean required() default false;
}
