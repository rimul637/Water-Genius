package com.watergenius.water

import kotlin.random.Random

internal val chapterNames = listOf("Green Garden", "Magic Forest", "Desert Oasis", "Frozen Valley", "Tropical Paradise")

internal object Levels {
    // Each seed is stable so installed copies retain the same puzzles after an update.
    private val seeds = intArrayOf(
        99, 547, 712, 602, 991, 1211, 835, 517, 473, 553,
        2918, 3128, 987, 1611, 2897, 5831, 3331, 2196, 6921, 1887,
        301, 9018, 7382, 4212, 7232, 9325, 4624, 9189, 2099, 8394,
        1589, 3225, 6832, 3316, 5527, 4651, 9904, 9180, 4096, 3818,
        2818, 2199, 2913, 7055, 2881, 1789, 23456, 98721, 3072, 5834
    )
    val all: List<Level> by lazy { seeds.mapIndexed { i, seed -> generate(i + 1, seed) } }

    private fun generate(number: Int, seed: Int): Level {
        val chapter = (number - 1) / 10
        val stage = (number - 1) % 10
        val width = 4 + chapter + if (stage >= 6 && chapter < 4) 1 else 0
        val height = width - if (stage < 3) 1 else 0
        if (number == 1) {
            val masks = mapOf(0 to 2, 1 to 12, 5 to 5, 9 to 3, 10 to 10, 11 to 8)
            val tiles = List(12) { cell ->
                val mask = masks[cell] ?: 0
                when (cell) {
                    0 -> Tile(Piece.SOURCE, mask, true)
                    11 -> Tile(Piece.PLANT, mask, true)
                    else -> when (mask.countOneBits()) {
                        0 -> Tile(Piece.BLOCK, 0, true)
                        2 -> Tile(if (mask == 5 || mask == 10) Piece.STRAIGHT else Piece.ELBOW, mask, false)
                        else -> error("Invalid tutorial pipe")
                    }
                }
            }
            return Level(1, 4, 3, tiles, intArrayOf(0, 3, 0, 0, 0, 1, 0, 0, 0, 3, 1, 0))
        }
        val random = Random(seed)
        val size = width * height
        val masks = IntArray(size)
        val degrees = IntArray(size)
        val visited = BooleanArray(size)
        val active = mutableListOf<Int>()

        fun neighbor(cell: Int, direction: Int): Int = when (direction) {
            0 -> if (cell >= width) cell - width else -1
            1 -> if (cell % width != width - 1) cell + 1 else -1
            2 -> if (cell + width < size) cell + width else -1
            else -> if (cell % width > 0) cell - 1 else -1
        }

        fun link(cell: Int, other: Int, direction: Int) {
            masks[cell] = masks[cell] or directions[direction]
            masks[other] = masks[other] or directions[opposite(direction)]
            degrees[cell]++
            degrees[other]++
        }

        val root = random.nextInt(width)
        val terminalGoal = when (chapter) {
            0 -> if (stage < 4) 2 else 3
            4 -> 7
            else -> 3 + chapter
        }
        val target = (size * (0.61 + stage * 0.022 + chapter * 0.026)).toInt().coerceIn(6, size - 1)
        val maxDegree = if (chapter == 0 && stage < 4) 2 else if (chapter < 3) 3 else 4
        visited[root] = true
        active += root
        var included = 1
        var leaves = 0
        while (included < target && active.isNotEmpty()) {
            val index = if (random.nextInt(100) < 62) active.lastIndex else random.nextInt(active.size)
            val cell = active[index]
            val candidates = directions.indices.shuffled(random).filter { direction ->
                val other = neighbor(cell, direction)
                other >= 0 && !visited[other]
            }
            val canBranch = degrees[cell] <= 1 || leaves < terminalGoal
            if (candidates.isEmpty() || degrees[cell] >= maxDegree || !canBranch || (cell == root && degrees[root] != 0)) {
                active.removeAt(index)
                continue
            }
            val direction = candidates.first()
            val other = neighbor(cell, direction)
            if (degrees[cell] != 1) leaves++
            link(cell, other, direction)
            visited[other] = true
            active += other
            included++
        }

        if (chapter >= 3) {
            val cycleCandidates = masks.indices.flatMap { cell ->
                (1..2).mapNotNull { direction ->
                    val other = neighbor(cell, direction)
                    if (other >= 0 && degrees[cell] >= 2 && degrees[other] >= 2 &&
                        (masks[cell] and directions[direction]) == 0
                    ) cell to direction else null
                }
            }.shuffled(random)
            for ((cell, direction) in cycleCandidates.take(1 + stage / 3 + (chapter - 3))) {
                link(cell, neighbor(cell, direction), direction)
            }
        }

        val terminals = masks.indices.filter { degrees[it] == 1 && it != root }
        val sources = buildSet {
            add(root)
            if (chapter == 4 && terminals.size > 1) add(terminals.last())
        }

        val straights = masks.indices.filter { masks[it] == 5 || masks[it] == 10 }.shuffled(random)
        val valves = if (chapter >= 2) straights.take(1 + stage / 5).toSet() else emptySet()
        val tiles = masks.indices.map { cell ->
            val mask = masks[cell]
            val piece = when {
                cell in sources -> Piece.SOURCE
                cell in terminals -> Piece.PLANT
                mask == 0 -> Piece.BLOCK
                cell in valves -> Piece.VALVE
                degrees[cell] == 4 -> Piece.CROSS
                degrees[cell] == 3 -> Piece.TEE
                mask == 5 || mask == 10 -> Piece.STRAIGHT
                else -> Piece.ELBOW
            }
            val fixed = piece in setOf(Piece.SOURCE, Piece.PLANT, Piece.BLOCK, Piece.CROSS) ||
                (chapter >= 3 && piece != Piece.VALVE && (cell + stage) % 13 == 0)
            Tile(piece, mask, fixed)
        }

        val initial = IntArray(size) { cell ->
            val tile = tiles[cell]
            if (tile.fixed) 0 else {
                val chance = 38 + chapter * 9 + stage * 2
                if (random.nextInt(100) < chance) random.nextInt(1, tile.period) else 0
            }
        }
        val level = Level(number, width, height, tiles, initial)
        if (flow(level, initial).complete) {
            val movable = tiles.indices.first { !tiles[it].fixed }
            initial[movable] = (initial[movable] + 1) % tiles[movable].period
            return Level(number, width, height, tiles, initial)
        }
        return level
    }
}
