package com.aiusage.app.data.provider

import com.aiusage.app.data.model.ErrorKind

class ProviderException(
    val kind: ErrorKind,
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

internal fun authError(message: String = "密钥无效，或没有访问该套餐的权限"): Nothing =
    throw ProviderException(ErrorKind.AUTH, message)

internal fun noPlanError(message: String = "没有读取到可用的额度信息，请确认套餐已开通"): Nothing =
    throw ProviderException(ErrorKind.NO_PLAN, message)

internal fun parseError(message: String = "接口返回的数据格式已变化"): Nothing =
    throw ProviderException(ErrorKind.PARSE, message)
