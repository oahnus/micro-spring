package com.github.oahnus.microspring.demo.web;

import com.github.oahnus.microspring.demo.service.DemoService;
import com.github.oahnus.microspring.framework.annotation.Autowired;
import com.github.oahnus.microspring.framework.annotation.Controller;
import com.github.oahnus.microspring.framework.annotation.RequestMapping;
import com.github.oahnus.microspring.framework.annotation.RequestParam;

/**
 * Created by oahnus on 2019/4/14
 * 21:09.
 */
@Controller
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    private DemoService demoService;

    @RequestMapping("/hello")
    public String hello(@RequestParam("name") String name) {
        return demoService.sayHello(name);
    }
}
