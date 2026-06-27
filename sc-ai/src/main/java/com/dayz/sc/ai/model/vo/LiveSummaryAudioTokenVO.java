package com.dayz.sc.ai.model.vo;

/**
 * LiveSummaryAudioTokenVO.
 *
 * @author DaYZ
 */
public record LiveSummaryAudioTokenVO(
        String token,
        long expiresInSeconds
) {
}
