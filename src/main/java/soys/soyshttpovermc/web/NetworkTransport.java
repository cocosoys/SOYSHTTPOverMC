package soys.soyshttpovermc.web;

/**
 * 网络传输实例化入口（<b>预留接口，暂不接入加载链路</b>）。
 *
 * <p>未来版本将用本接口统一「网络页（{@link NetworkPage}）」与「首页远程拉取（{@code web.home}
 * 网络 URL）」的底层传输层，支持开发者注入自定义传输（如加密/签名校验/私有协议）。
 * 当前版本：调用 {@code WebPageApi.registerNetworkTransport(transport)} 仅作占位存储与日志，
 * <b>不参与任何加载</b>——网络页仍由 {@link NetworkPage#load()} 自行实现传输。</p>
 */
public interface NetworkTransport {

    /** 传输提供者唯一名称（日志/调试用）。 */
    String name();

    /** 按 URL 获取内容字节（自定义协议/加密在实现内处理；失败抛异常）。 */
    byte[] fetch(String url) throws Exception;
}
