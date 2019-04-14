package com.github.oahnus.microspring.framework;

import com.github.oahnus.microspring.framework.annotation.Autowired;
import com.github.oahnus.microspring.framework.annotation.Controller;
import com.github.oahnus.microspring.framework.annotation.RequestMapping;
import com.github.oahnus.microspring.framework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by oahnus on 2019/4/13
 * 21:21.
 */
public class DispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();
    private List<String> classNames = new ArrayList<>();

    // 模拟ioc容器 保存Bean 实例
    private Map<String, Object> ioc = new HashMap<>();

    private List<Handler> handlers = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            resp.getWriter().println("500 Server Inner Error");
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        if (handlers.isEmpty()) {
            resp.getWriter().println("404");
        }
        // 获取相对路径
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String url = uri.replaceAll(contextPath, "").replaceAll("/+", "/");

        Handler requestHandler = null;

        for (Handler handler : handlers) {
            if (handler.getUrlPattern().matcher(url).matches()) {
                requestHandler = handler;
                break;
            }
        }

        if (requestHandler == null) {
            resp.getWriter().println("404");
            return;
        }

        // Controller 方法中 添加了@requestParam 的变量的类型
        Class<?>[] paramTypes = requestHandler.getParamTypes();

        // 请求参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 请求参数的值
        Object[] paramValues = new Object[paramTypes.length];
        // Controller 方法中 添加了@requestParam 的变量名及 变量index
        Map<String, Integer> requestParamsInfoMap = requestHandler.getRequestParamsInfoMap();

        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String reqParamName = entry.getKey();

            String value = Arrays.toString(entry.getValue())
                    .replaceAll("[\\[\\]]", "")
                    .replaceAll("\\s", ",");

            // 请求参数名字没有添加@RequestParam 直接跳过
            if (!requestParamsInfoMap.containsKey(reqParamName)) {
                continue;
            }
            Integer idx = requestParamsInfoMap.get(reqParamName);
            paramValues[idx] = convert(paramTypes[idx], value);
        }

        // 绑定reqeust
        if (requestParamsInfoMap.containsKey(HttpServletRequest.class.getName())) {
            Integer idx = requestParamsInfoMap.get(HttpServletRequest.class.getName());
            paramValues[idx] = req;
        }

        // 绑定response
        if (requestParamsInfoMap.containsKey(HttpServletResponse.class.getName())) {
            Integer idx = requestParamsInfoMap.get(HttpServletResponse.class.getName());
            paramValues[idx] = resp;
        }

        // 反射 执行Controller中对应的方法
        Method handlerMethod = requestHandler.getMethod();
        Object retVal = handlerMethod.invoke(requestHandler.getController(), paramValues);
        if (retVal == null || handlerMethod.getReturnType() == void.class) {
            return;
        }
        // 返回
        resp.getWriter().println(retVal.toString());
    }

    private Object convert(Class<?> typeClass, String val) {
        if (typeClass == Integer.class) {
            return Integer.valueOf(val);
        }
        // todo
        else {
            return val;
        }
    }

    /**
     * 初始化方法
     * 读取配置文件, 扫包， 创建IOC容器并初始化Bean的实例，注册HandlerMapping
     * SpringMVC 的dispatcherServlet 是在onRefresh方法内初始化的
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2 扫描相关的类
        doScanClass(contextConfig.getProperty("basePackage"));

        // 3 初始化Bean, 将Bean实例保存到IOC容器中
        doCreateInstance();

        // 4 依赖注入
        doAutoWired();

        // 5 绑定HandlerMapping
        doBindHandlerMapping();

        System.out.println("Init Success");
    }

    private void doBindHandlerMapping() {
        if (this.ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Object instance = entry.getValue();

            Class<?> clazz = instance.getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }

            // 获取Controller 上的RequestMapping注解, 设置baseUrl
            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }

            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }

                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = baseUrl + "/" + requestMapping.value()
                        .replace("/+", "/")
                        .replaceFirst("/", "");

                handlers.add(new Handler(url, instance,  method));
            }
        }
    }

    /**
     * 依赖注入
     */
    private void doAutoWired() {
        if (this.ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Object instance = entry.getValue();

            // 获取 Bean中声明的所有字段
            Field[] fields = instance.getClass().getDeclaredFields();

            for (Field field : fields) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                // 查找添加@Autowired注解的字段， 使用反射为其赋值
                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value().trim();

                // 获取接口的类型
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                // 反射赋值
                field.setAccessible(true);

                try {
                    field.set(instance, ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 实例化Bean 并且添加Bean到IOC容器
     */
    private void doCreateInstance() {
        if (this.classNames.isEmpty()) {
            return;
        }
        try {
            // 遍历classNames 创建Bean实例
            // classNames 保存 完整包路径 xxx.xxx.xxx.ClassName
            for (String className : classNames) {
                Class clazz = Class.forName(className);
                // @Controller 初始化放入ioc
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = getBeanName(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                }
                // @Service
                else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = (Service) clazz.getAnnotation(Service.class);
                    // 是否有自定义Bean name
                    String beanName = service.value();
                    if (beanName.trim().equals("")) {
                        beanName = getBeanName(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();

                    Class[] interfaces = clazz.getInterfaces();

                    ioc.put(beanName, instance);

                    // 如果Service有继承接口, 将接口类型作为Key , instance为val存入ioc
                    for (Class intf : interfaces) {
                        if (ioc.containsKey(intf.getName())) {
                            throw new RuntimeException(intf.getName() + " is existed");
                        }
                        ioc.put(intf.getName(), instance);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将className 首字母小写
     * @param className
     * @return
     */
    private String getBeanName(String className) {
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * 扫描配置的basePackage 包下的所有.class 文件
     * @param basePackage 要扫描的包路径 xxx.xxx.xxx
     */
    private void doScanClass(String basePackage) {
        // todo system separator
        URL url = this.getClass().getClassLoader().getResource(basePackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File f : classPath.listFiles()) {
            if (f.isDirectory()) {
                doScanClass(basePackage + "." + f.getName());
            } else {
                if (!f.getName().endsWith(".class")) {
                    continue;
                }
                String className = basePackage + "." + f.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }


    /**
     * 加载配置文件
     * @param contextConfigLocation 配置文件path
     */
    private void doLoadConfig(String contextConfigLocation) {
//        @Cleanup InputStream in = this.getClass().getResourceAsStream(contextConfigLocation);
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation)) {
            contextConfig.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
