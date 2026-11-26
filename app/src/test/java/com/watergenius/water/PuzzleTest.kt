package com.watergenius.water

import org.junit.Assert.*
import org.junit.Test

class PuzzleTest {
    @Test fun campaignIsUniqueAndReplayable() {
        assertEquals(50, Levels.all.size)
        val signatures = hashSetOf<List<Int>>()
        for (level in Levels.all) {
            val signature = level.tiles.map { it.solution }
            assertTrue("Duplicate board ${level.number}", signatures.add(signature))
            assertTrue("No plants on ${level.number}", level.plants > 0)
            assertTrue("Level ${level.number} starts solved", !flow(level, level.initial).complete)
            assertTrue("Missing solution on ${level.number}", flow(level, IntArray(level.tiles.size)).complete)
            if (level.chapter == 4) assertEquals(2, level.tiles.count { it.piece == Piece.SOURCE })
            val rotations = level.initial.copyOf()
            var moves = 0
            var water = flow(level, rotations)
            while (!water.complete && moves <= level.par) {
                val next = requireNotNull(hint(level, rotations, water))
                assertTrue("Hint incorrectly points to a non-moving tile", !level.tiles[next.cell].fixed)
                rotations[next.cell] = (rotations[next.cell] + next.turns) % level.tiles[next.cell].period
                moves += next.turns
                water = flow(level, rotations)
            }
            assertTrue("Hints failed on ${level.number}", water.complete)
            assertTrue(moves <= level.par)
            assertNull(hint(level, rotations, water))
        }
    }

    @Test fun blockedClosedAndLeakingNetworksDoNotWin() {
        val level = Level(21, 3, 1, listOf(
            Tile(Piece.SOURCE, 2, true),
            Tile(Piece.VALVE, 10, false),
            Tile(Piece.PLANT, 8, true)
        ), intArrayOf(0, 1, 0))
        val stalled = flow(level, level.initial)
        assertEquals(0, stalled.watered)
        assertEquals(2, stalled.leaks[0])
        assertTrue(flow(level, intArrayOf(0, 2, 0)).complete)
        val withLeak = Level(21, 3, 1, level.tiles.toMutableList().apply {
            this[1] = Tile(Piece.TEE, 11, false)
        }, IntArray(3))
        assertFalse(flow(withLeak, withLeak.initial).complete)
        assertEquals(1, flow(withLeak, withLeak.initial).watered)
    }

    @Test fun scoringAndProgressionPreserveBestResult() {
        assertEquals(3, stars(12, 12, 0))
        assertEquals(2, stars(12, 13, 0))
        assertEquals(2, stars(12, 8, 1))
        assertEquals(1, stars(12, 8, 2))
        assertEquals(1, stars(12, 17, 0))
        assertEquals(1, Progress().next)
        val progress = Progress(best = mapOf(1 to 3, 2 to 2, 4 to 1))
        assertEquals(3, progress.next)
        assertEquals(6, progress.total)
        assertFalse(progress.unlocked(4))
        val final = Progress(best = (1..50).associateWith { 3 })
        assertEquals(50, final.next)
        assertEquals(150, final.total)
    }
}
