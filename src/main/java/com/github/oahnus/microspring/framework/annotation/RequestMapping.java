package com.github.oahnus.microspring.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by oahnus on 2019/4/13
 * 21:20.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {
    String value() default "";
    String[] method() default {};
}
