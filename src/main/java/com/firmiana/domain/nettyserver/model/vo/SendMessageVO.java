package com.firmiana.domain.nettyserver.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
/**
 * @date 2021-07-16
 * @author oujunting
 */
@Data
public class SendMessageVO implements Serializable {

    @ApiModelProperty(name = "标题")
    private String title;

    @ApiModelProperty(name = "内容")
    private String content;
}
