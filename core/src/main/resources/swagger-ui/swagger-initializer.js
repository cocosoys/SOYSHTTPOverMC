// SOYS Swagger 初始化：标准 SwaggerUI 装配 + 调试身份栏（切换玩家 / 粘贴 X-API-Key）。
//
// 调试身份只作用于本页面发出的接口调试请求（经 requestInterceptor 附加请求头），
// 凭证仅存于本页面 JS 变量（刷新即失），不写 document.cookie / localStorage——
// 因此切换调试身份不会影响浏览器自身的 SOYS 登录凭证，也不会导致下次进不了 /swagger/ui/index。
(function () {
    "use strict";

    var DEBUG_BASE = "/swagger/debug";
    var DEBUG_GUEST_HEADER = "X-Soys-Debug-Guest"; // 与后端 AuthUtils.DEBUG_GUEST_HEADER 保持一致
    var debugToken = null;         // 切换玩家签发的 st_ Bearer（仅内存）
    var debugKey = null;           // 粘贴并校验通过的 X-API-Key（仅内存）
    var debugGuestNonce = null;    // 未登录游客模式：switch-guest 服务端签发的一次性 nonce（仅内存，TTL 5 分钟）
    var apiKeyHeader = "X-API-Key"; // auth.yml 可配头名，经 current-user 下发

    // ===== 调试栏渲染 =====

    function renderBar(state) {
        var bar = document.getElementById("soys-swagger-debug-bar");
        if (!bar) return;
        var label;
        if (state.mode === "token") {
            label = "调试身份: " + state.label + "（Bearer）";
        } else if (state.mode === "apikey") {
            label = "X-API-Key: " + state.label;
        } else if (state.mode === "guest") {
            label = "未登录游客（无任何身份凭证）";
        } else {
            label = "当前用户: " + state.label;
        }
        bar.innerHTML =
            '<div style="font-weight:600;margin-bottom:4px;">SOYS 调试身份</div>' +
            '<div id="soys-sw-status">' + label + "</div>" +
            '<div style="margin-top:6px;display:flex;gap:4px;flex-wrap:wrap;">' +
            '<input id="soys-sw-player" placeholder="切换玩家名" style="width:96px;font-size:12px;padding:2px 4px;box-sizing:border-box;">' +
            '<button id="soys-sw-do" type="button" style="font-size:12px;padding:2px 8px;">切换</button>' +
            "</div>" +
            '<div style="margin-top:4px;display:flex;gap:4px;flex-wrap:wrap;">' +
            '<input id="soys-sw-key" placeholder="粘贴 X-API-Key" style="width:96px;font-size:12px;padding:2px 4px;box-sizing:border-box;">' +
            '<button id="soys-sw-keydo" type="button" style="font-size:12px;padding:2px 8px;">使用</button>' +
            "</div>" +
            '<div style="margin-top:4px;">' +
            '<button id="soys-sw-reset" type="button" style="font-size:12px;padding:2px 8px;">恢复当前用户</button>' +
            "</div>" +
            '<div style="margin-top:4px;">' +
            '<button id="soys-sw-guest" type="button" style="font-size:12px;padding:2px 8px;">切换到未登录游客</button>' +
            "</div>" +
            '<div id="soys-sw-msg" style="margin-top:4px;color:#a14e50;font-size:11px;line-height:1.4;"></div>';
        bindBar();
    }

    function bindBar() {
        var doBtn = document.getElementById("soys-sw-do");
        var keyBtn = document.getElementById("soys-sw-keydo");
        var resetBtn = document.getElementById("soys-sw-reset");
        var guestBtn = document.getElementById("soys-sw-guest");
        if (doBtn) doBtn.onclick = switchPlayer;
        if (keyBtn) keyBtn.onclick = useKey;
        if (resetBtn) resetBtn.onclick = resetDebug;
        if (guestBtn) guestBtn.onclick = switchGuest;
    }

    function msg(text) {
        var el = document.getElementById("soys-sw-msg");
        if (el) el.textContent = text;
    }

    // ===== 当前用户 =====

    function getCurrentUser() {
        fetch(DEBUG_BASE + "/current-user")
            .then(function (r) { return r.json(); })
            .then(function (j) {
                if (j && j.code === 200) {
                    apiKeyHeader = j.apiKeyHeader || "X-API-Key";
                    renderBar({ mode: "cookie", label: j.player || "?" });
                }
            })
            .catch(function () { /* 守卫未放行时保持默认显示 */ });
    }

    // ===== 切换玩家（登录认证签发 Bearer） =====

    function switchPlayer() {
        var input = document.getElementById("soys-sw-player");
        var name = input ? input.value.trim() : "";
        if (!name) { msg("请输入玩家名"); return; }
        fetch(DEBUG_BASE + "/switch-player", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ player: name })
        })
            .then(function (r) { return r.json(); })
            .then(function (j) {
                if (j && j.code === 200 && j.token) {
                    debugToken = j.token;
                    debugKey = null;
                    debugGuestNonce = null;
                    renderBar({ mode: "token", label: j.player || name });
                    msg("已切换为 " + (j.player || name) + "（仅本页调试请求生效，刷新自动恢复）");
                } else {
                    msg((j && j.msg) || "切换失败");
                }
            })
            .catch(function () { msg("切换请求失败"); });
    }

    // ===== 粘贴 X-API-Key（先经 /verify-key 校验，再原样注入） =====

    function useKey() {
        var input = document.getElementById("soys-sw-key");
        var key = input ? input.value.trim() : "";
        if (!key) { msg("请粘贴 X-API-Key"); return; }
        fetch(DEBUG_BASE + "/verify-key", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ key: key })
        })
            .then(function (r) { return r.json(); })
            .then(function (j) {
                if (j && j.code === 200) {
                    debugKey = key;
                    debugToken = null;
                    debugGuestNonce = null;
                    var label = j.boundPlayer || ("指纹 " + (j.fingerprint || "?"));
                    renderBar({ mode: "apikey", label: label });
                    msg("已启用 X-API-Key（仅本页调试请求生效）");
                } else {
                    msg((j && j.msg) || "X-API-Key 无效");
                }
            })
            .catch(function () { msg("校验请求失败"); });
    }

    // ===== 恢复 =====

    function resetDebug() {
        debugToken = null;
        debugKey = null;
        debugGuestNonce = null;
        renderBar({ mode: "cookie", label: "…" });
        msg("已恢复浏览器当前用户身份");
        getCurrentUser();
    }

    // ===== 未登录游客（先经 switch-guest 获取服务端签发的 nonce，调试请求才被剥离身份） =====

    function switchGuest() {
        fetch(DEBUG_BASE + "/switch-guest", { method: "POST" })
            .then(function (r) { return r.json(); })
            .then(function (j) {
                if (j && j.code === 200 && j.nonce) {
                    debugToken = null;
                    debugKey = null;
                    debugGuestNonce = j.nonce;
                    renderBar({ mode: "guest", label: "未登录游客" });
                    msg("已切换为未登录游客：调试请求携带服务端签发的 nonce（" +
                        (j.ttlSeconds || 300) + " 秒有效），后端核验通过后剥离 X-API-Key / Bearer / Cookie 身份");
                } else {
                    msg((j && j.msg) || "获取游客 nonce 失败");
                }
            })
            .catch(function () { msg("游客切换请求失败"); });
    }

    // ===== SwaggerUI 装配 =====

    window.onload = function () {
        window.ui = SwaggerUIBundle({
            url: "../api-docs",
            dom_id: "#swagger-ui",
            deepLinking: true,
            presets: [
                SwaggerUIBundle.presets.apis,
                SwaggerUIStandalonePreset
            ],
            plugins: [
                SwaggerUIBundle.plugins.DownloadUrl
            ],
            layout: "StandaloneLayout",
            // 调试身份注入：游客 → 附加服务端签发 nonce（后端核验后才剥离身份）；
            // 切换玩家 → Bearer；粘贴 X-API-Key → 对应头名；均无 → 回落浏览器 cookie
            requestInterceptor: function (req) {
                if (debugGuestNonce) {
                    req.headers = req.headers || {};
                    req.headers[DEBUG_GUEST_HEADER] = debugGuestNonce;
                } else if (debugToken) {
                    req.headers = req.headers || {};
                    req.headers.Authorization = "Bearer " + debugToken;
                } else if (debugKey) {
                    req.headers = req.headers || {};
                    req.headers[apiKeyHeader] = debugKey;
                }
                return req;
            }
        });

        // 浮动调试栏（右上角，不干扰 Swagger 布局）
        var bar = document.createElement("div");
        bar.id = "soys-swagger-debug-bar";
        bar.style.cssText = "position:fixed;top:8px;right:8px;z-index:99999;background:#fff;" +
            "border:1px solid #d0d0d0;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.15);" +
            "padding:8px 10px;font:12px/1.5 sans-serif;color:#222;max-width:340px;";
        document.body.appendChild(bar);
        renderBar({ mode: "cookie", label: "…" });
        getCurrentUser();
    };
})();
