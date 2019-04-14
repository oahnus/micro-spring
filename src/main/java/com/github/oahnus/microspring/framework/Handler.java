package com.github.oahnus.microspring.framework;

import com.github.oahnus.microspring.framework.annotation.RequestParam;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by oahnus on 2019/4/14
 * 0:04.
 */
@Data
public class Handler {

    private Pattern urlPattern;
    private Method method;
    private Object controller;
    private Map<String, Integer> requestParamsInfoMap = new HashMap<>();
    private Class<?>[] paramTypes;

    public Handler(String url, Object controller, Method method) {
        this.method = method;
        this.controller = controller;
        this.urlPattern = Pattern.compile(url);

        this.paramTypes = method.getParameterTypes();

        init(method);
    }

    private void init(Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        Class<?>[] parameterTypes = method.getParameterTypes();

        for (Annotation[] annotations : parameterAnnotations) {
            for (int i = 0; i <annotations.length; i++) {
                Annotation annotation = annotations[i];
                if (annotation instanceof RequestParam) {

                    String paramName = ((RequestParam) annotation).value();

                    requestParamsInfoMap.put(paramName.trim(), i);
                }
            }
        }

        for (int i = 0; i <parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                requestParamsInfoMap.put(type.getName(), i);
            }
        }
    }
}
