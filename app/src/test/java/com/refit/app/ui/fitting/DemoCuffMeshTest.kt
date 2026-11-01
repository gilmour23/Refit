package com.refit.app.ui.fitting

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DemoCuffMeshTest {
    @Test fun exportedMeshIsClosedAndHasConsistentEdgeDirections() {
        val counts = mutableMapOf<Set<DemoCuffMesh.Point>, Int>()
        val directions = mutableSetOf<Pair<DemoCuffMesh.Point, DemoCuffMesh.Point>>()
        val mesh = DemoCuffMesh.triangles()
        mesh.forEach { t ->
            listOf(t.a to t.b, t.b to t.c, t.c to t.a).forEach { (a,b) ->
                assertNotEquals(a,b)
                assertTrue("Duplicate directed edge", directions.add(a to b))
                val edge = setOf(a,b)
                counts[edge] = (counts[edge] ?: 0) + 1
            }
        }
        assertTrue(counts.isNotEmpty())
        assertTrue("Every edge must belong to exactly two faces", counts.values.all { it == 2 })
        assertTrue(directions.all { (a,b) -> b to a in directions })
        val vertices = mesh.flatMap { listOf(it.a,it.b,it.c) }
        assertEquals(100.0, vertices.maxOf { it.y }-vertices.minOf { it.y }, .0001)
    }

    @Test fun stlUsesDecimalPointsRegardlessOfDeviceLocaleAndLabelsDemo() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val stl = DemoCuffMesh.stl()
            assertTrue(stl.startsWith("solid REFIT_DEMO_NOT_PATIENT_FIT_MM"))
            assertFalse(stl.contains("NaN"))
            assertFalse(stl.contains("Infinity"))
            assertFalse(stl.contains(","))
            assertEquals(DemoCuffMesh.triangles().size, Regex("facet normal").findAll(stl).count())
        } finally { Locale.setDefault(previous) }
    }

    @Test fun handoffExplicitlyIdentifiesSyntheticDimensionsAndNonClinicalUse() {
        val json = DemoCuffMesh.json("left", .45f, .5f)
        assertTrue(json.contains("synthetic_fixture_not_image_analysis"))
        assertTrue(json.contains("\"guide_lines_used_for_measurement\": false"))
        assertTrue(json.contains("\"suitable_for_patient_use\": false"))
    }
}
