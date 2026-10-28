package moe.ouom.neriplayer.data.platform.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliAudioSelectorTest {

    @Test
    fun prioritizeBiliStreamUrls_keepsOverseasAkamaiPrimaryAheadOfDomesticBackup() {
        val overseas = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/demo.m4s"
        val domestic = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/demo.m4s"

        assertEquals(listOf(overseas, domestic), prioritizeBiliStreamUrls(overseas, listOf(domestic)))
        assertEquals(listOf(domestic, overseas), prioritizeBiliStreamUrls(domestic, listOf(overseas)))
    }

    @Test
    fun prioritizeBiliStreamUrls_preservesApiOrderAcrossRegularCdnHosts() {
        val primary = "https://cn.example.bilivideo.cn/upgcxcode/demo.m4s"
        val backup = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/demo.m4s"

        assertEquals(listOf(primary, backup), prioritizeBiliStreamUrls(primary, listOf(backup, primary)))
    }

    @Test
    fun prioritizeBiliStreamUrls_prefersBilivideoHostsOverMountaintoys() {
        val prioritized = prioritizeBiliStreamUrls(
            primaryUrl = "https://b-demo.edge.mountaintoys.cn/upgcxcode/demo.m4s",
            backupUrls = listOf(
                "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/demo.m4s",
                "https://xy123x45x67x89xy.mcdn.bilivideo.cn:8082/v1/resource/demo.m4s"
            )
        )

        assertEquals(
            "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/demo.m4s",
            prioritized.first()
        )
        assertTrue(prioritized.last().contains("mcdn.bilivideo.cn"))
    }

    @Test
    fun prioritizeBiliStreamUrls_pushesUnstableP2pCdnToLastEvenWhenPrimary() {
        val prioritized = prioritizeBiliStreamUrls(
            primaryUrl = "https://xy123x45x67x89xy.mcdn.bilivideo.cn:8082/v1/resource/demo.m4s",
            backupUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/demo.m4s",
                "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/demo.m4s"
            )
        )

        assertTrue(prioritized.last().contains("mcdn.bilivideo.cn"))
        assertEquals(3, prioritized.size)
    }

    @Test
    fun isUnstableBiliP2pCdnUrl_matchesMcdnAndPort8082() {
        assertTrue(isUnstableBiliP2pCdnUrl("https://xy1.mcdn.bilivideo.com/v1/resource/x.m4s"))
        assertTrue(isUnstableBiliP2pCdnUrl("https://xy1.example.com:8082/v1/resource/x.m4s"))
        assertTrue(!isUnstableBiliP2pCdnUrl("https://upos-sz-mirrorali.bilivideo.com/upgcxcode/x.m4s"))
        assertTrue(!isUnstableBiliP2pCdnUrl("https://upos-hz-mirrorakam.akamaized.net/upgcxcode/x.m4s"))
    }

    @Test
    fun selectStreamByPreference_usesRealisticBiliBitrates() {
        val mediumStream = BiliAudioStreamInfo(
            id = 30232,
            mimeType = "audio/mp4",
            bitrateKbps = 92,
            qualityTag = null,
            url = "https://xy.example.bilivideo.cn/30232.m4s"
        )
        val lowStream = BiliAudioStreamInfo(
            id = 30216,
            mimeType = "audio/mp4",
            bitrateKbps = 48,
            qualityTag = null,
            url = "https://upos.example.bilivideo.com/30216.m4s"
        )
        val highStream = BiliAudioStreamInfo(
            id = 30280,
            mimeType = "audio/mp4",
            bitrateKbps = 200,
            qualityTag = null,
            url = "https://xy.example.mcdn.bilivideo.cn/30280.m4s"
        )
        val streams = listOf(lowStream, mediumStream, highStream)

        assertEquals(30280, selectStreamByPreference(streams, "high")?.id)
        assertEquals(30232, selectStreamByPreference(streams, "medium")?.id)
        assertEquals(30232, selectStreamByPreference(streams, "low")?.id)
    }

    @Test
    fun selectStreamByPreference_losslessPrefersRealFlacTrack() {
        val flacStream = BiliAudioStreamInfo(
            id = 30251,
            mimeType = "audio/flac",
            bitrateKbps = 1411,
            qualityTag = "hires",
            url = "https://upos.example.bilivideo.com/30251.m4s"
        )
        val highStream = BiliAudioStreamInfo(
            id = 30280,
            mimeType = "audio/mp4",
            bitrateKbps = 200,
            qualityTag = null,
            url = "https://upos.example.bilivideo.com/30280.m4s"
        )

        assertEquals(30251, selectStreamByPreference(listOf(highStream, flacStream), "lossless")?.id)
    }

    @Test
    fun selectStreamByPreference_normalizesQualityTagsBeforeDowngrade() {
        val hiresStream = BiliAudioStreamInfo(
            id = 30251,
            mimeType = "audio/flac",
            bitrateKbps = 1411,
            qualityTag = "HIRES",
            url = "https://upos.example.bilivideo.com/30251-hires.m4s"
        )
        val highStream = BiliAudioStreamInfo(
            id = 30280,
            mimeType = "audio/mp4",
            bitrateKbps = 200,
            qualityTag = null,
            url = "https://upos.example.bilivideo.com/30280.m4s"
        )

        assertEquals(30251, selectStreamByPreference(listOf(highStream, hiresStream), "hires")?.id)
    }

    @Test
    fun isBiliStreamHost_matchesMountaintoysEdgeDomain() {
        assertTrue(isBiliStreamHost("b-demo.edge.mountaintoys.cn"))
        assertTrue(isBiliStreamUrl("https://b-demo.edge.mountaintoys.cn/upgcxcode/demo.m4s"))
    }
}
