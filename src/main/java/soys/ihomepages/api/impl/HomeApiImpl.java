package soys.ihomepages.api.impl;

import soys.ihomepages.api.HomeApi;
import soys.ihomepages.homepage.HomepageRegistry;
import soys.ihomepages.homepage.HomepageState;

import java.util.List;

/**
 * {@link HomeApi} 实现：委托 {@link HomepageRegistry}（运行时注册/切换/注销）与
 * {@link HomepageState}（当前选择持久化）。
 */
public class HomeApiImpl implements HomeApi {

    private final HomepageRegistry registry;
    private final HomepageState state;

    public HomeApiImpl(HomepageRegistry registry, HomepageState state) {
        this.registry = registry;
        this.state = state;
    }

    @Override
    public void register(String name, String ownerPlugin, byte[] content, String contentType) {
        registry.register(name, ownerPlugin, content, contentType);
    }

    @Override
    public boolean registerSource(String name, String ownerPlugin, String sourceSpec) {
        return registry.registerSource(name, ownerPlugin, sourceSpec);
    }

    @Override
    public boolean switchTo(String name) {
        return registry.switchTo(name);
    }

    @Override
    public boolean unregister(String name) {
        return registry.unregister(name);
    }

    @Override
    public int unregisterAll() {
        return registry.unregisterAll();
    }

    @Override
    public List<String> list() {
        return registry.list();
    }

    @Override
    public String getCurrent() {
        return registry.getCurrentName();
    }

    @Override
    public void persist(String name) {
        state.saveCurrent(name);
    }
}