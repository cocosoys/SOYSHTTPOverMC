package com.github.cocosoys.mc.soyshttpovermc.spring.impl;

import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.PingEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.SystemInfoEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.entity.vo.WhoAmIEntityVO;
import com.github.cocosoys.mc.soyshttpovermc.spring.service.ISystemService;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;

/**
 * 系统级 Service 实现（仿 MyBatis-Plus 的 {@code XxxServiceImpl extends ServiceImpl implements XxxService}）：
 * <b>业务数据在此组装</b>，控制器只调用接口方法。
 */
public class SystemServiceImpl extends BaseServiceImpl<SystemInfoEntityVO> implements ISystemService {

    private final int port;

    public SystemServiceImpl(int port) {
        this.port = port;
    }

    @Override
    public PingEntityVO ping() {
        PingEntityVO data = new PingEntityVO();
        data.setPong(true);
        data.setTime(System.currentTimeMillis());
        data.setName("SOYSHTTPOverMC");
        data.setPort(port);
        data.setOnline(true);
        return data;
    }

    @Override
    public SystemInfoEntityVO getVersion() {
        return new SystemInfoEntityVO("SOYSHTTPOverMC", "1.0.0",
                "三协议端口: MC / 明文 HTTP / HTTPS", port);
    }

    @Override
    public WhoAmIEntityVO whoAmI(ApiRequestContext ctx) {
        WhoAmIEntityVO data = new WhoAmIEntityVO();
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
