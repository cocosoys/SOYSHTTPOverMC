package soys.soyshttpovermc.api.impl;

import soys.soyshttpovermc.api.AuthCredentialApi;
import soys.soyshttpovermc.exception.AuthException;
import soys.soyshttpovermc.exception.ExceptionBus;
import soys.soyshttpovermc.i18n.I18n;
import soys.soyshttpovermc.gateway.GatewayFilter;
import soys.soyshttpovermc.gateway.policy.auth.issuer.CredentialIssuer;
import soys.soyshttpovermc.gateway.policy.auth.issuer.IssuedCredential;
import soys.soyshttpovermc.gateway.policy.auth.issuer.SessionTokenIssuer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 能力组 3：鉴权与凭证（委托 {@link GatewayFilter} / {@link CredentialIssuer}）。
 * 由 {@link SoysHttpOverMcApiImpl} 组合并对外暴露；实现 {@link AuthCredentialApi}。
 */
public class AuthCredentialImpl implements AuthCredentialApi {

    private final GatewayFilter gateway;

    public AuthCredentialImpl(GatewayFilter gateway) {
        this.gateway = gateway;
    }

    @Override
    public void registerCredentialIssuer(String name, Supplier<CredentialIssuer> factory) {
        try {
            GatewayFilter.registerIssuer(name, factory);
        } catch (Exception ex) {
            throw ExceptionBus.fire(new AuthException("E_REGISTER_ISSUER", "exception.auth.register-issuer-fail", "注册凭证颁发器失败(name={0}): {1}", ex, name, ex.getMessage()));
        }
    }

    @Override
    public boolean isAuthEnabled() {
        return gateway != null && gateway.isAuthEnabled();
    }

    @Override
    public List<String> getIssuerNames() {
        List<String> names = new ArrayList<>();
        if (gateway != null) for (CredentialIssuer i : gateway.getIssuers()) names.add(i.name());
        return names;
    }

    @Override
    public IssuedCredential issueCredential(String subject) {
        return issueCredential(null, subject);
    }

    @Override
    public IssuedCredential issueCredential(String issuerName, String subject) {
        return issueCredential(issuerName, subject, null);
    }

    @Override
    public IssuedCredential issueCredential(String subject, java.util.Map<String, String> claims) {
        return issueCredential(null, subject, claims);
    }

    @Override
    public IssuedCredential issueCredential(String issuerName, String subject, java.util.Map<String, String> claims) {
        if (gateway == null || subject == null) return null;
        for (CredentialIssuer i : gateway.getIssuers()) {
            if (!i.isEnabled()) continue;
            if (issuerName != null && !issuerName.equals(i.name())) continue;
            try {
                if (claims != null && !claims.isEmpty() && i instanceof SessionTokenIssuer) {
                    return ((SessionTokenIssuer) i).issue(subject, null, claims);
                }
                return i.issue(subject);
            } catch (Exception ex) {
                throw ExceptionBus.fire(new AuthException("E_ISSUE", "exception.auth.issue-fail", "签发凭证失败(issuer={0}, subject={1}): {2}", ex, i.name(), subject, ex.getMessage()));
            }
        }
        return null;
    }
}
