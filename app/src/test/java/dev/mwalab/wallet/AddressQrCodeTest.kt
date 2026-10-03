package dev.mwalab.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddressQrCodeTest {
    @Test
    fun knownDevnetAddressMatchesQrVersion3LowMask0ReferenceMatrix() {
        val payload = "8u3XMxFwZ7NmpSKk619MsrmZkMTskzrrcq5LwFA1TEqb"
        val expected = listOf(
            "#######..#..#..#.###..#######",
            "#.....#...###.##.###..#.....#",
            "#.###.#.###.#......##.#.###.#",
            "#.###.#..###.##..####.#.###.#",
            "#.###.#...#...#..#.#..#.###.#",
            "#.....#..#....###..##.#.....#",
            "#######.#.#.#.#.#.#.#.#######",
            "........###.##.###...........",
            "###.#####.##.....#...##...#..",
            "####.#.#..##.#......#.#..##..",
            "...#.###.#....#..##...###.###",
            ".####......#.###.#####..#...#",
            "...#####....###....####...#.#",
            "#....#.#...####.#..##.....#.#",
            "########.#.##.#.......#..##.#",
            ".#..##.#.#..###..###..#.#....",
            "...#.##....#....#..#.##.##.##",
            ".##..#...###.##.###..#...##.#",
            "#.#...#...#.....#......#..###",
            ".#.###.#...#.#...#.#..#....#.",
            "#.###.#..#..#....#..#####.#..",
            "........#..###..###.#...#...#",
            "#######.#..##...#####.#.#...#",
            "#.....#.#.#.###..####...#..#.",
            "#.###.#.##.###...#..######.#.",
            "#.###.#...##.##.#..##...#..##",
            "#.###.#.#...#.#.#####...##..#",
            "#.....#.#.#..##..##.#####..#.",
            "#######.###.##.#.#..##...####",
        )

        val matrix = AddressQrCode.encode(payload)
        assertEquals(AddressQrCode.SIZE, matrix.size)
        val actual = (0 until matrix.size).map { y ->
            buildString { for (x in 0 until matrix.size) append(if (matrix.isDark(x, y)) '#' else '.') }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun receiveQrRejectsEmptyAndOversizedPayloads() {
        assertThrows(IllegalArgumentException::class.java) { AddressQrCode.encode("") }
        assertThrows(IllegalArgumentException::class.java) { AddressQrCode.encode("0not-base58") }
        assertThrows(IllegalArgumentException::class.java) {
            AddressQrCode.encode("A".repeat(AddressQrCode.MAX_PAYLOAD_BYTES + 1))
        }
    }
}
