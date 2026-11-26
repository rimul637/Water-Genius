package com.watergenius.water

internal enum class Piece { BLOCK, SOURCE, PLANT, STRAIGHT, ELBOW, TEE, CROSS, VALVE }
internal val directions = intArrayOf(1, 2, 4, 8)
internal fun opposite(direction: Int) = (direction + 2) % 4
internal fun turn(mask: Int) = ((mask shl 1) and 15) or (mask ushr 3)

internal data class Tile(val piece: Piece, val solution: Int, val fixed: Boolean) {
    val period: Int get() = when {
        fixed -> 1
        piece == Piece.STRAIGHT -> 2
        else -> 4
    }

    fun openings(rotation: Int): Int {
        if (piece == Piece.VALVE && rotation % 2 != 0) return 0
        var mask = solution
        repeat(rotation % 4) { mask = turn(mask) }
        return mask
    }

    fun clockwiseToSolution(rotation: Int): Int {
        val equivalent = if (piece == Piece.VALVE) 2 else period
        return (equivalent - rotation % equivalent) % equivalent
    }
}

internal class Level(
    val number: Int,
    val width: Int,
    val height: Int,
    val tiles: List<Tile>,
    val initial: IntArray
) {
    val chapter get() = (number - 1) / 10
    val par = tiles.indices.sumOf { tiles[it].clockwiseToSolution(initial[it]) }
    val plants = tiles.count { it.piece == Piece.PLANT }

    fun adjacent(cell: Int, direction: Int): Int {
        val column = cell % width
        val row = cell / width
        return when (direction) {
            0 -> if (row > 0) cell - width else -1
            1 -> if (column + 1 < width) cell + 1 else -1
            2 -> if (row + 1 < height) cell + width else -1
            else -> if (column > 0) cell - 1 else -1
        }
    }
}

internal data class Flow(
    val masks: IntArray,
    val distance: IntArray,
    val leaks: IntArray,
    val watered: Int,
    val complete: Boolean
) {
    fun wet(cell: Int) = distance[cell] >= 0
}

internal fun flow(level: Level, rotations: IntArray): Flow {
    val masks = IntArray(level.tiles.size) { level.tiles[it].openings(rotations[it]) }
    val distance = IntArray(masks.size) { -1 }
    val leaks = IntArray(masks.size)
    val queue = IntArray(masks.size)
    var head = 0
    var tail = 0
    var watered = 0
    for (cell in masks.indices) {
        if (level.tiles[cell].piece == Piece.SOURCE) {
            queue[tail++] = cell
            distance[cell] = 0
        }
    }
    while (head < tail) {
        val cell = queue[head++]
        if (level.tiles[cell].piece == Piece.PLANT) watered++
        for (direction in directions.indices) {
            val bit = directions[direction]
            if (masks[cell] and bit == 0) continue
            val neighbor = level.adjacent(cell, direction)
            if (neighbor < 0 || masks[neighbor] and directions[opposite(direction)] == 0) {
                leaks[cell] = leaks[cell] or bit
            } else if (distance[neighbor] < 0) {
                distance[neighbor] = distance[cell] + 1
                queue[tail++] = neighbor
            }
        }
    }
    return Flow(masks, distance, leaks, watered, watered == level.plants && leaks.all { it == 0 })
}

internal data class Hint(val cell: Int, val turns: Int)

internal fun hint(level: Level, rotations: IntArray, water: Flow): Hint? {
    if (water.complete) return null
    var best: Hint? = null
    var bestPriority = Int.MIN_VALUE
    for (cell in level.tiles.indices) {
        val turns = level.tiles[cell].clockwiseToSolution(rotations[cell])
        if (turns == 0) continue
        var priority = if (water.wet(cell)) 8 else 0
        for (direction in directions.indices) {
            val neighbor = level.adjacent(cell, direction)
            if (neighbor >= 0 && water.leaks[neighbor] and directions[opposite(direction)] != 0) {
                priority += if (level.tiles[cell].solution and directions[direction] != 0) 16 else 2
            }
        }
        priority -= turns
        if (priority > bestPriority) {
            best = Hint(cell, turns)
            bestPriority = priority
        }
    }
    return best
}

internal fun stars(par: Int, moves: Int, hints: Int): Int = when {
    hints == 0 && moves <= par -> 3
    hints <= 1 && moves <= par + maxOf(3, par / 3) -> 2
    else -> 1
}
