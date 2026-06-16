package com.dayz.sc.gateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 网关接口文档入口跳转
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Controller
public class GatewayApiDocRedirectController {
    @GetMapping("/doc.html")
    public String docHtml() {
        return "redirect:/scalar";
    }
}
