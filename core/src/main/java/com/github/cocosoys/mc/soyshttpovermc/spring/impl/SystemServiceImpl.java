package com.github.cocosoys.mc.soyshttpovermc.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.PingEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.SystemInfoEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.WhoAmIEntity;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ISystemService;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;

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
    public PingEntity ping() {
        PingEntity data = new PingEntity();
        data.setPong(true);
        data.setTime(System.currentTimeMillis());
        data.setName("SOYSHTTPOverMC");
        data.setPort(port);
        data.setOnline(true);
        return data;
    }

    @Override
    public SystemInfoEntity getVersion() {
        return new SystemInfoEntity("SOYSHTTPOverMC", "1.0.0",
                "三协议端口: MC / 明文 HTTP / HTTPS", port);
    }

    @Override
    public WhoAmIEntity whoAmI(ApiRequestContext ctx) {
        WhoAmIEntity data = new WhoAmIEntity();
        if (ctx != null) {
            data.setIp(ctx.getIp());
            data.setMethod(ctx.getHttpMethod());
            data.setPath(ctx.getPath());
            data.setAuthenticated(ctx.isAuthenticated());
            data.setPlayer(ctx.getPlayerName());
            data.setOnline(ctx.getPlayer() != null);
        }
        return data;
    }
}
