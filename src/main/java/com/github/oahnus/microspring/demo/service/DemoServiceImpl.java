package com.github.oahnus.microspring.demo.service;

import com.github.oahnus.microspring.framework.annotation.Service;

/**
 * Created by oahnus on 2019/4/14
 * 21:10.
 */
@Service
public class DemoServiceImpl implements DemoService{
    @Override
    public String sayHello(String name) {
        return "Hello " + name;
    }
}
