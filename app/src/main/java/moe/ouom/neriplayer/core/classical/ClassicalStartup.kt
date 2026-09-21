package moe.ouom.neriplayer.core.classical

/**
 * Classical 开屏零确认：fresh install 也视为 disclaimer / onboarding 已完成，
 * 首帧直接进 Main，不展示 Disclaimer → Onboarding 链路。
 * official 原样以存储值为准。
 */
fun effectiveDisclaimerAccepted(
    stored: Boolean?,
    classical: Boolean = ClassicalLowPower.enabled
): Boolean = stored == true || classical

fun effectiveOnboardingCompleted(
    stored: Boolean?,
    classical: Boolean = ClassicalLowPower.enabled
): Boolean = stored == true || classical
