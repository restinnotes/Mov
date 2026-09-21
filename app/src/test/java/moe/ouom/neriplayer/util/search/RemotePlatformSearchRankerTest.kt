package moe.ouom.neriplayer.util.search

import moe.ouom.neriplayer.core.api.bili.BiliClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bilibili 外部搜索质量回归测试.
 *
 * 数据来源: 2026-09-19 对真实 Bilibili `x/web-interface/wbi/search/type` API 的实测
 * (query=`维格四重奏` / `Vegh Quartet` / `Végh Quartet` / `Backhaus` 等, 各取 RAW 前 20).
 * 核心结论写入代码约束:
 * - Neri 与 BBPlayer 的 RAW BVID 顺序实测完全一致, 问题在客户端排序, 不在 HTTP 参数;
 * - SearchTextMatcher 通用 matcher 不动, 远端特有逻辑收敛在本包的 RemotePlatformSearchRanker.
 */
class RemotePlatformSearchRankerTest {

    private fun video(
        bvid: String,
        title: String,
        author: String = "someone",
        typeid: Int = 59,
        typename: String = "演奏",
        tag: String = "",
        description: String = "",
        hitColumns: List<String> = emptyList()
    ): BiliClient.SearchVideoItem = BiliClient.SearchVideoItem(
        aid = bvid.hashCode().toLong(),
        bvid = bvid,
        titleHtml = title,
        titlePlain = title,
        author = author,
        mid = 1L,
        coverUrl = "",
        durationSec = 100,
        play = null,
        pubdate = null,
        typeid = typeid,
        typename = typename,
        tag = tag,
        description = description,
        hitColumns = hitColumns
    )

    @Test
    fun `exact vegh entity beats generic quartets without vegh`() {
        val items = listOf(
            video("BV-dover", "【Dover String Quartet】海顿：降B大调弦乐四重奏-日出", "Ewerk"),
            video("BV-vio", "维瓦尔第 四季 春 Vio String Quartet", "老时的弦外之音"),
            video(
                "BV-vegh", "String Quartets No.1~6,op.18-Vegh Quartet",
                "阿图尔_施纳贝尔",
                tag = "演奏,大师,弦乐四重奏,维格四重奏,Vegh Quartet,古典,早期,贝多芬,弦乐,室内乐",
                hitColumns = listOf("title", "description", "tag")
            ),
            video("BV-hagen", "THE HAGEN QUARTET ~ Mozart String Quartet in D major , K.499", "muzikair")
        )
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Vegh Quartet", items)
        // server #3 的 exact 命中必须到顶, 其余保持 server 相对顺序
        assertEquals(listOf("BV-vegh", "BV-dover", "BV-vio", "BV-hagen"), ranked.map { it.bvid })
    }

    @Test
    fun `chinese entity not buried by generic quartet token`() {
        val items = listOf(
            video("BV-hiphop", "【安全着陆/直火帮】《四重奏》交响乐配hiphop", "吟风踏雪0", typeid = 130),
            video("BV-dover", "【Dover String Quartet】海顿：降B大调弦乐四重奏-日出", "Ewerk"),
            video(
                "BV-vegh", "【维格四重奏 | Valois 1973-74】贝多芬弦乐四重奏全集 - Beethoven String Quartets - Quatuor Végh",
                "MusikSphinx",
                tag = "经典录音全集,弦乐四重奏,维格四重奏,古典音乐,弦乐,室内乐,Quatuor Végh,贝多芬",
                hitColumns = listOf("title", "tag")
            )
        )
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("维格四重奏", items)
        assertEquals("BV-vegh", ranked.first().bvid)
        // Dover 这类缺实体的不能靠 quartet/四重奏压过实体
        assertTrue(ranked.map { it.bvid }.indexOf("BV-dover") > 0)
    }

    @Test
    fun `latin accent query folds to ascii entity`() {
        val items = listOf(
            video("BV-noise", "维瓦尔第 四季 春 Vio String Quartet", "老时的弦外之音"),
            video("BV-vegh", "String Quartets No.1~6,op.18-Vegh Quartet", "阿图尔_施纳贝尔")
        )
        // Végh 经 NFKD 归一为 vegh, 必须命中 ascii 实体 (无中文译名干扰的对照组)
        assertNotNull(RemotePlatformSearchRanker.score("Végh Quartet", items[1]))
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Végh Quartet", items)
        assertEquals("BV-vegh", ranked.first().bvid)
    }

    @Test
    fun `server description hit rescues title mismatch instead of sinking to tail`() {
        // 实测 Backhaus server #3: title 无 latin, description 含 Wilhelm Backhaus, hit=[description].
        // 修前: title/author 打分 null, 从 server #3 沉底到 #19.
        val rescued = video(
            "BV-rescued", "【贝多芬/巴克豪斯】钢琴奏鸣曲全辑[立体声处理]", "塞琉西亚拆迁队",
            description = "作曲：Ludwig van Beethoven\n演奏：Wilhelm Backhaus（威廉·巴克豪斯）",
            hitColumns = listOf("description")
        )
        val junk = video("BV-junk", "TIKTOK LIVE BATTLE BATWEEN YOUSIF PAKISTANI VS DR EMAAN", "x", typeid = 21)
        assertNotNull(RemotePlatformSearchRanker.score("Backhaus", rescued))
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Backhaus", listOf(junk, rescued))
        assertEquals("BV-rescued", ranked.first().bvid)
    }

    @Test
    fun `server tag hit rescues without description`() {
        val rescued = video(
            "BV-tagged", "[Hi-Res]【威尔海姆·巴克豪斯】贝多芬：钢琴奏鸣曲全集 [24-96]", "y",
            tag = "电台新星征集令,Wilhelm Backhaus,Hi-Res,奏鸣曲,钢琴演奏,古典音乐,贝多芬,钢琴",
            description = "BT Composed By – Ludwig van Beethoven Piano – Wilhelm Backhaus",
            hitColumns = listOf("description", "tag")
        )
        assertNotNull(RemotePlatformSearchRanker.score("Backhaus", rescued))
    }

    @Test
    fun `hit gated fuzzy noise across mixed text stays unmatched`() {
        // 实测 Vegh Quartet server #18 `Ghost Quartet`: tag 被平台标命中 (因 quartet),
        // 但 vegh 只是跨 Dave…Ghost 几十字符的 fuzzy subsequence (score=206).
        // 缺实体的 generic quartet 绝不能因此上浮.
        val ghost = video(
            "BV-ghost", "【自翻】幽灵四重奏 (Ghost Quartet) - 2015年外百老汇原版",
            "阿克拉", typeid = 130,
            tag = "Dave Malloy,音乐剧,大彗星,Ghost Quartet",
            description = "Dave Malloy 音乐剧 Ghost Quartet 外百老汇",
            hitColumns = listOf("title", "description", "tag")
        )
        assertNull(RemotePlatformSearchRanker.score("Vegh Quartet", ghost))
        val hagen = video(
            "BV-hagen", "THE HAGEN QUARTET ~ Mozart String Quartet in D major", "muzikair"
        )
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Vegh Quartet", listOf(ghost, hagen))
        // 全员 null: 保持 server 相对顺序, 谁也不许插队
        assertEquals(listOf("BV-ghost", "BV-hagen"), ranked.map { it.bvid })
    }

    @Test
    fun `description without server hit does not create false matches`() {
        // 平台未标明 description 命中时, 长描述不得参与匹配 (防 fuzzy 误伤).
        val item = video(
            "BV-x", "四重奏一夜涨粉百万，被老师发现后。。。", "z",
            description = "Wilhelm Backhaus played here, but server did not flag it",
            hitColumns = listOf("title")
        )
        assertNull(RemotePlatformSearchRanker.score("Backhaus", item))
    }

    @Test
    fun `unmatched items keep server relative order`() {
        val items = listOf(
            video("BV-a", "Ether Strike.mp4", "a", typeid = 136),
            video("BV-b", "ether strike 1粉", "b", typeid = 136),
            video("BV-c", "【Arcaea音乐欣赏】Ether Strike", "c", typeid = 136)
        )
        // 全员 null (与 Beethoven String Quartet 实测一致): server 顺序原样返回
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Beethoven String Quartet", items)
        assertEquals(listOf("BV-a", "BV-b", "BV-c"), ranked.map { it.bvid })
    }

    @Test
    fun `music section gets mild boost without overriding match quality`() {
        // 同分 (同 title 不同分区): 音乐区靠前 (结论 C: 全部分区 + 音乐区轻微 boost).
        val nonMusic = video("BV-nm", "富特文格勒 贝多芬第九交响曲", "a", typeid = 184, typename = "预告·资讯")
        val music = video("BV-m", "富特文格勒 贝多芬第九交响曲", "a", typeid = 59, typename = "演奏")
        assertEquals(
            listOf("BV-m", "BV-nm"),
            RemotePlatformSearchRanker.rankBiliVideos("富特文格勒", listOf(nonMusic, music)).map { it.bvid }
        )
        // 但 boost 绝不允许模糊匹配压过精确实体匹配.
        val exactNonMusic = video("BV-exact", "Backhaus 巴克豪斯50年代版贝多芬钢琴奏鸣曲全集", "a", typeid = 184)
        val fuzzyMusic = video("BV-fuzzy", "Backhauus piano works (typo kept on purpose)", "b", typeid = 59)
        val ranked = RemotePlatformSearchRanker.rankBiliVideos("Backhaus", listOf(fuzzyMusic, exactNonMusic))
        val exactScore = RemotePlatformSearchRanker.score("Backhaus", exactNonMusic)
        val fuzzyScore = RemotePlatformSearchRanker.score("Backhaus", fuzzyMusic)
        assertNotNull(exactScore)
        assertNotNull(fuzzyScore)
        assertTrue(exactScore!! < fuzzyScore!!)
        assertEquals("BV-exact", ranked.first().bvid)
    }

    @Test
    fun `blank query and tiny lists pass through`() {
        val items = listOf(video("BV-a", "x"), video("BV-b", "y"))
        assertEquals(items, RemotePlatformSearchRanker.rankBiliVideos("  ", items))
        assertEquals(items.take(1), RemotePlatformSearchRanker.rankBiliVideos("q", items.take(1)))
    }
}
