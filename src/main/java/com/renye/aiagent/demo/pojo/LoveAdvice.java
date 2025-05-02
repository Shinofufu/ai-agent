package com.renye.aiagent.demo.pojo;


import lombok.Data;

@Data
class LoveAdvice{
    String situationSummary;
    String suggestion;
    int confidenceRating;
}
