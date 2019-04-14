package com.github.oahnus.microspring.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by oahnus on 2019/4/13
 * 21:25.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {
    String value() default "";
}
