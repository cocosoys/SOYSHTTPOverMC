# Introduction

Welcome to the **SOYSHTTPOverMC Developer Tutorial**.

SOYSHTTPOverMC (HTTP-Over-MC) is a Spigot plugin that turns your Minecraft server into a web server: on the same port the server already listens on, it serves **Minecraft traffic, plain HTTP and HTTPS** at once, and provides annotation-based REST APIs, web page hosting, a security gateway, multi-backend storage with ORM, and internationalization. Browsers and game clients share the same address and port — no extra process, no extra port.

## Supported Versions (Real Build Artifacts)

The repository uses a multi-module + version-adapter architecture. One core source covers multiple server versions, delivered as separate jars:

| Artifact | Target Server | Same-Port Sniffing | Java Requirement |
| --- | --- | --- | --- |
| `SOYSHTTPOverMC-1_6-<version>.jar` | 1.6.4 | Connection-level interception (ServerSocket first-byte dispatch) | JDK 7/8 |
| `SOYSHTTPOverMC-1_7-<version>.jar` | 1.7.10 | Relocated-netty reflection bridge (hooking the server's embedded netty) | JDK 7/8 |
| `SOYSHTTPOverMC-1_12-<version>.jar` | 1.12.2 | Netty pipeline injection (standard netty, full feature set) | JDK 8 |
| `adapter/v1_16x`, `v1_20x`, `v1_21x`, `v1_26x` | 1.16.x / 1.20.x / 1.21.x / 1.26.x | Version-specific reflection adapter modules | Per-version JDK |

> This tutorial uses **Spigot 1.12.2 + Java 8** as the running example by default; all APIs and configuration have identical semantics on the other supported versions.

## Core Capabilities

1. **Three protocols on one port**: MC handshake, plain HTTP and HTTPS (TLS upgraded in place) share the server port without interfering;
2. **Annotation-based REST API**: `@GetMapping` / `@PostMapping` etc. expose HTTP endpoints (Spring-MVC-like), automatically prefixed with `/api`;
3. **Web page & static resource hosting**: programmatic (`WebRegistry`) and config-file (`pages.yml`) channels, with nickname routes, redirects, directory hosting, caching and permission protection;
4. **Security gateway**: pluggable policy chain (TLS enforcement, IP allow/deny lists, token-bucket rate limiting, access limiter, unified auth), all configured via YAML under `gateway/`;
5. **Login plugin integration**: AuthMe and others connect through an SPI; players can log in from the web and obtain session tokens (JWT), including "remember me" device auto-login;
6. **Multi-backend storage + dual-backend ORM**: YAML / SQLite / MySQL primary-secondary mirroring; `@TableName` entity annotations with one condition-chain API on both `YAML.Pojo` and `SQL.Pojo`;
7. **Local permission table**: built-in `local` permission provider (`/soyshttp perm` manages users/groups/permissions), unified online & offline checks;
8. **Internationalization**: `language.yml` switches languages and stacks extra language sources; all plugin logs and UI text are translatable;
9. **Multi-version compatibility**: adapter version modules cover per-version differences via reflection / SPI, exposing a fully consistent API to third-party plugins.

## What This Tutorial Gives You

After reading it you will be able to:

1. **Register annotation-based REST APIs** in your plugin, exposing HTTP endpoints with minimal code;
2. Host **web pages and static resources** programmatically or via config, and control access permissions;
3. Protect your endpoints with the **security gateway** (TLS, IP allowlist, rate limiting, API keys, session tokens);
4. Read and write **YAML files and SQL databases** through one uniform set of ORM annotations;
5. Correctly configure **cross-server routing** in a proxy (BungeeCord / Velocity) setup;
6. Understand the **capability facade API** (6 capability groups) and the event system for plugin interop.

## Target Audience

- You already write Spigot / Paper plugins (familiar with `JavaPlugin`, `onEnable`, event listeners);
- You know Java 8 syntax and basic collections;
- You have a basic idea of HTTP / JSON / REST (no expertise needed).

If you are new to plugin development, finish a "HelloWorld" plugin first and come back.

## Chapter Navigation

| Chapter | Content |
| --- | --- |
| [Introduction](Introduction.md) | This page: motivation, goals and navigation |
| [Chapter 1 Overview & Quick Start](Chapter-1-Overview-and-Quick-Start.md) | Architecture overview, install & artifacts, directory layout, commands, first integration |
| [Chapter 2 Annotation-based Web API](Chapter-2-Annotated-WebAPI-Development.md) | Annotation set, parameter binding, unified response, permissions, registration |
| [Chapter 3 Web Pages & Static Resources](Chapter-3-Web-Pages-and-Static-Resources.md) | WebRegistry, pages.yml, caching, large files, page permissions, error pages, CORS |
| [Chapter 4 Authentication & Security](Chapter-4-Authentication-and-Security.md) | Gateway policy chain, credential system, login plugins, auto-login, combined permissions |
| [Chapter 5 Data Storage & ORM](Chapter-5-Data-Storage-and-ORM.md) | Primary-secondary storage, entity annotations, YAML/SQL backends, condition chain, migration |
| [Chapter 6 Advanced & Best Practices](Chapter-6-Advanced-Capabilities-and-Best-Practices.md) | HTTP client, extensions, interceptors, custom policies, I18n, multi-version builds |
| [Chapter 7 Event System](Chapter-7-Event-System.md) | All event types, listening patterns, typical uses |
| [Appendix API Reference](Appendix-API-Reference.md) | Facade capability groups, WebRegistry, command & config index |

## Reading Conventions

- All code samples are Java 8 syntax, targeting **Spigot / Paper 1.12.2 + Java 8** (per-version differences are covered in each chapter's "Version Notes").
- `api` in code always refers to the facade obtained via `HttpOverMcPlugin.getInstance().getApi()` (see Chapter 1).
- "The main plugin" means SOYSHTTPOverMC itself; "host plugin / third-party plugin" means the plugin that integrates these capabilities.
- Method signatures, config paths and default values in this documentation were verified against the current source and can be used as a reference.

Ready? Start with [Chapter 1 Overview & Quick Start](Chapter-1-Overview-and-Quick-Start.md).
