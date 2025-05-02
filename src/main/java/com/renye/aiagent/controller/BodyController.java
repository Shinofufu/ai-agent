package com.renye.aiagent.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/body")
@Tag(name = "body参数")
public class BodyController {

    @GetMapping()
    public String check() {
        return "ok";
    }
}
