package soys.soyshttpovermc.spring.impl;

import soys.soyshttpovermc.spring.entity.SystemInfoEntity;
import soys.soyshttpovermc.spring.service.ISystemService;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统级 Service 实现（仿 MyBatis-Plus 的 {@code XxxServiceImpl extends ServiceImpl implements XxxService}）：
 * <b>业务数据在此组装</b>，控制器只调用接口方法。
 */
public class SystemServiceImpl extends BaseServiceImpl<SystemInfoEntity> implements ISystemService {

    private final int port;

    public SystemServiceImpl(int port) {
        this.port = port;
    }

    @Override
    public Map<String, Object> ping() {
        Map<String, Object> data = new HashMap<>();
        data.put("pong", true);
        data.put("time", System.currentTimeMillis());
        data.put("name", "SOYSHTTPOverMC");
        data.put("port", port);
        data.put("online", true);
        return data;
    }

    @Override
    public SystemInfoEntity getVersion() {
        return new SystemInfoEntity("SOYSHTTPOverMC", "1.0.0",
                "三协议端口: MC / 明文 HTTP / HTTPS", port);
    }
}
