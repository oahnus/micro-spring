package com.github.oahnus.microspring.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by oahnus on 2019/4/13
 * 21:24.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {
    String value() default "";
}
