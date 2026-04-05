package com.example.marketing.user.mapper;

import com.example.marketing.user.dto.UserDto;
import com.example.marketing.user.dto.UserResponseDto;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.Mapper;

@Mapper(uses = BaseMapper.class)
public interface UserMapper extends BaseMapper<UserResponseDto,UserEntity> {
}
