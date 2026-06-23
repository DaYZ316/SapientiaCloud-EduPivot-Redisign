# LiveKit Docker 自部署说明

本项目使用自部署 LiveKit Server 承载课堂直播音视频转发，浏览器入口固定为：

```text
wss://edupivot.xyz/livekit
```

## 必要环境变量

在服务器 `.env` 中补充以下变量；`.env` 已被 `.gitignore` 忽略，不要提交真实密钥。

```text
LIVEKIT_URL=wss://edupivot.xyz/livekit
LIVEKIT_SERVER_URL=http://livekit:7880
LIVEKIT_API_KEY=replace-with-livekit-api-key
LIVEKIT_API_SECRET=replace-with-livekit-api-secret
LIVEKIT_IMAGE=livekit/livekit-server:latest
LIVEKIT_HTTP_PORT=7880
LIVEKIT_RTC_TCP_PORT=7881
LIVEKIT_RTC_UDP_PORT=7882
LIVEKIT_API_BIND_HOST=127.0.0.1
LIVEKIT_RTC_BIND_HOST=0.0.0.0
```

`LIVEKIT_URL` 会返回给浏览器；`LIVEKIT_SERVER_URL` 只给后端容器调用 LiveKit Room Service。

## DNS 与证书

- 默认使用现有主域名路径 `/livekit/`，不需要额外 DNS。
- Nginx 已增加 `/livekit/` 反代，转发 LiveKit API/WebSocket 到 `7880`。
- 如需改回 `livekit.edupivot.xyz` 独立子域名，需要先添加 DNS，并确保证书包含该子域名。

## 端口要求

服务器防火墙和云厂商安全组至少开放：

```text
80/tcp
443/tcp
7881/tcp
7882/udp
```

`7880/tcp` 默认只绑定 `127.0.0.1`，由 Nginx 反代，不需要直接暴露到公网。
`7882/udp` 使用 LiveKit UDP mux，避免 Docker 发布 `50000-60000/udp` 这种大范围端口导致启动慢或运行内存飙升。

## 启动与检查

```bash
docker compose config
docker compose up -d livekit-config-init livekit
docker compose logs -f livekit
```

课堂直播联调检查：

- 后端 `/api/class-sessions/{id}/live-token` 返回 `url=wss://edupivot.xyz/livekit`。
- 教师开始直播后可发布摄像头、麦克风、屏幕共享。
- 学生入座后可观看画面并听到声音。
- 学生离座后直播断开，课程结束后 LiveKit 房间被 best-effort 删除。

## 备注

本次未启用 TURN。若学生处在企业/校园严格网络下，出现 UDP/TCP fallback 仍无法连接，再补充 TURN/UDP 或 TURN/TLS。
