package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.AttrAttrgroupRelationEntity;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class GroupVo extends AttrGroupEntity /*implements Serializable */{

    private List<AttrEntity> attrEntities;
    private List<AttrAttrgroupRelationEntity> relations;

}
