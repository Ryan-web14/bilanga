package com.sni.bilanga.audit.aop;


import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    String ressource() default "";
    String module();
    String action();
}
