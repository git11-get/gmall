package com.atguigu.gmall.search.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "goods",type = "info",shards = 3,replicas = 2)
public class Goods {

    @Id
    private Long skuId;
    @Field(type = FieldType.Keyword,index = false)
    private String pic; //图片地址
    @Field(type = FieldType.Text,analyzer = "ik_max_word")
    private String title;
    @Field(type = FieldType.Double)
    private Double price;
    @Field(type = FieldType.Long)
    private Long sale; //销量

    @Field(type = FieldType.Boolean)
    private Boolean store; //是否有货

    @Field(type = FieldType.Date)
    private Date createTime; //新品
    @Field(type = FieldType.Long)
    private Long brandId; //品牌id
    @Field(type = FieldType.Keyword)
    private String brandName; //品牌名称
    @Field(type = FieldType.Long)
    private Long categoryId; //分类Id
    @Field(type = FieldType.Keyword)
    private String categoryName; //分类名称
    @Field(type = FieldType.Nested)
    private List<SearchAttr> attrs;
}
