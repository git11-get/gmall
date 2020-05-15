package com.atguigu.gmall.ums.dao;

import com.atguigu.gmall.ums.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author fengge
 * @email lxf@atguigu.com
 * @date 2020-05-06 18:46:04
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
