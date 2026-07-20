package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.DeviceBindingEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 设备绑定 Mapper。
 */
public interface DeviceBindingMapper extends BaseMapper<DeviceBindingEntity> {

    /**
     * 按 device_id 查询设备绑定（含 credential_hash）。
     */
    DeviceBindingEntity selectByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 按 device_id 查询并锁定设备行（用于事务内串行处理）。
     */
    DeviceBindingEntity selectByDeviceIdForUpdate(@Param("deviceId") String deviceId);

    /**
     * 更新凭证哈希（Token 轮换）。
     */
    int updateCredentialHash(@Param("id") Long id,
                              @Param("credentialHash") String credentialHash,
                              @Param("now") java.time.Instant now);
}
