package com.grw.xiaobai.hybrid.llm.config;


import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * swagger2配置
 */
@Configuration
@ConditionalOnExpression("${swagger.enable}")
public class SwaggerConfig {
    @Bean(value = "dockerBean")
    public Docket dockerBean() {
        //指定使用Swagger3规范
        Docket docket = new Docket(DocumentationType.OAS_30)
                .apiInfo(new ApiInfoBuilder()
                        //描述字段支持Markdown语法
                        .description("llm command")
                        .termsOfServiceUrl("localhost:8091")
                        .contact(new Contact("sgw", "", ""))
                        .version("3.0.0")
                        .build())
                //分组名称
                .groupName("llm")
                .enable(true)
                .select()
                //这里指定Controller扫描包路径
                .apis(RequestHandlerSelectors.withClassAnnotation(RestController.class))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }
}