package com.dayz.aeroverse.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 后端接口文档入口跳转。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Controller
public class ApiDocRedirectController {
    @GetMapping("/doc.html")
    public String docHtml() {
        return "redirect:/scalar";
    }
}
