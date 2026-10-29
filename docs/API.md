# 供应商接口备忘

所有请求均为 GET，`Authorization: Bearer <key>`，除非特别说明。应用不经过中转服务器。

## 智谱 GLM / Z.ai

- 国内：`https://open.bigmodel.cn/api/monitor/usage/quota/limit`
- 国际：`https://api.z.ai/api/monitor/usage/quota/limit`
- 国内使用 `Authorization: Bearer <key>`；国际接口按社区实现使用裸 `Authorization: <key>`（应用已按区域区分）。
- 返回结构：

```json
{
  "code": 0,
  "success": true,
  "data": {
    "level": "pro",
    "limits": [
      {
        "type": "TOKENS_LIMIT",
        "unit": 3,
        "number": 5,
        "percentage": 26,
        "nextResetTime": "2026-10-28T18:00:00Z"
      },
      {
        "type": "TOKENS_LIMIT",
        "unit": 6,
        "number": 1,
        "percentage": 40,
        "nextResetTime": 1790000000000
      }
    ]
  }
}
```

- `unit=3, number=5` → 5 小时；`unit=6, number=1` → 周。
- `percentage` 是“已使用百分比”，应用转换为剩余百分比。
- 没有公开的账户余额接口（社区验证），因此余额固定置灰。
- 参考：[GLM Coding Plan 文档](https://docs.bigmodel.cn/cn/coding-plan/overview)

## MiniMax M Plan / Token Plan

- 国际：`https://api.minimax.io/v1/token_plan/remains`
- 国内：`https://api.minimaxi.com/v1/token_plan/remains`
- 返回结构（字段可能为字符串或数字）：

```json
{
  "base_resp": { "status_code": 0, "status_msg": "success" },
  "data": {
    "current_subscribe_title": "M Plan Explore",
    "points_balance": "14000",
    "model_remains": [
      {
        "model_name": "general",
        "current_interval_remaining_percent": "96",
        "current_interval_status": 1,
        "start_time": 1780279200000,
        "end_time": 1780297200000,
        "current_weekly_remaining_percent": "99",
        "current_weekly_status": 1,
        "weekly_start_time": 1780243200000,
        "weekly_end_time": 1780848000000
      }
    ]
  }
}
```

- `current_*_remaining_percent` 是剩余百分比。
- 状态：`1` 正常、`2` 已用尽、`3` 不限量 / 套餐不含该窗口。
- 周状态为 `3` 时视为“该套餐没有周额度”，置灰。
- `points_balance` 为积分余额，1000 积分 = $1。
- MiniMax 在 HTTP 200 中用 `base_resp.status_code` 表示鉴权失败（如 1004 / 2049），应用会读取 body 判断。
- 参考：[M Plan FAQ](https://platform.minimax.io/docs/m-plan/faq)

## OpenCode Go

- `https://opencode.ai/zen/go/v1/usage`
- `Authorization: Bearer <key>`，建议带 `x-opencode-session`。
- 返回结构：

```json
{
  "usage": {
    "rolling": { "status": "ok", "percent": 40, "resetsAt": "2026-10-28T18:00:00Z" },
    "weekly": { "status": "ok", "percent": 12.5, "resetsAt": "2026-11-02T00:00:00Z" },
    "monthly": { "status": "ok", "percent": 60, "resetsAt": "2026-11-20T00:00:00Z" }
  }
}
```

- `percent` 是已使用百分比；`status` 为 `ok` / `rate-limited`。
- 该接口目前是 OpenCode 内部接口（未在公开文档中承诺稳定），应用对缺字段做了容错。
- 余额需要开启 Extra Usage 后在 Console 查看，API Key 不返回。
- 参考：[OpenCode Go 文档](https://opencode.ai/docs/go/)、[issue #31084](https://github.com/anomalyco/opencode/issues/31084)

## DeepSeek

- `https://api.deepseek.com/user/balance`
- 返回结构：

```json
{
  "is_available": true,
  "balance_infos": [
    { "currency": "CNY", "total_balance": "88.25", "granted_balance": "0.00", "topped_up_balance": "88.25" }
  ]
}
```

- 只有余额，没有 5 小时 / 周 / 月额度接口。
- 参考：[Get User Balance](https://api-docs.deepseek.com/api/get-user-balance/)

## Kimi Code

- 国内：`https://api.kimi.com/coding/v1/usages`
- 国际：`https://api.kimi.ai/coding/v1/usages`
- 使用 **Kimi Code 控制台**创建的 Key（`sk-kimi-…`），与 Moonshot 开放平台 Key 不互通。
- 返回结构：

```json
{
  "user": { "membership": { "level": "LEVEL_INTERMEDIATE" } },
  "usage": {
    "limit": "100",
    "used": "5",
    "remaining": "95",
    "resetTime": "2026-11-04T13:27:17.211Z"
  },
  "limits": [
    {
      "window": { "duration": 300, "timeUnit": "TIME_UNIT_MINUTE" },
      "detail": { "limit": "100", "remaining": "100", "resetTime": "2026-10-28T23:27:17.211Z" }
    }
  ]
}
```

- 顶层 `usage` 是周额度；`limits` 中的 5 小时窗口映射到 5 小时格。
- 使用 Kimi Code **API Key** 模式时只发送 Bearer，不发送 OAuth 客户端专用的 `X-Msh-*` 身份头。
- 月额度仅当接口返回 27–31 天窗口时展示；目前多数 Key 需要浏览器登录态，应用不采集 Cookie。
- 参考：[Kimi Code 文档](https://www.kimi.com/code/docs/)、[kimi-code-usage](https://github.com/Golden0Voyager/kimi-code-usage)

## Kimi 开放平台 / Moonshot

- 国内：`https://api.moonshot.cn/v1/users/me/balance`
- 国际：`https://api.moonshot.ai/v1/users/me/balance`
- 返回结构：

```json
{
  "code": 0,
  "data": {
    "available_balance": 12.34,
    "voucher_balance": 2.0,
    "cash_balance": 10.34
  }
}
```

- 使用非 `sk-kimi-` 前缀的 Moonshot Key 时，应用自动走余额接口。
- 参考：[Moonshot API](https://platform.moonshot.cn/docs)

## 错误与缓存策略

- 401/403 → 密钥无效或没有套餐权限。
- 429 → 频率限制。
- 5xx → 供应商服务异常。
- 解析失败 → “接口返回格式已变化”，保留上一次成功数据。
- 旧数据在刷新失败时不会清空，卡片显示“数据过期”并保留四格内容。
- 首次失败没有旧数据时，四格全部置灰，并展示错误原因。
