package com.refit.app.ui.fitting

import java.util.Locale
import kotlin.math.*

// A synthetic, open cuff for demonstrating the CAD handoff. Units are millimetres.
// These dimensions are deliberately independent of the selected photographs.
internal object DemoCuffMesh {
    const val wristWidth = 54.0
    const val wristDepth = 40.0
    const val length = 100.0
    const val shell = 3.0
    const val clearance = 2.0
    data class Point(val x: Double, val y: Double, val z: Double)
    data class Triangle(val a: Point, val b: Point, val c: Point)

    fun triangles(): List<Triangle> = buildList {
        val n = 48
        fun p(end: Int, i: Int, outer: Boolean): Point {
            val angle = Math.toRadians(40.0 + 280.0 * i / n)
            val extra = clearance + if (outer) shell else 0.0
            val rx = wristWidth / 2 + end * 7.0 + extra
            val rz = wristDepth / 2 + end * 6.0 + extra
            return Point(rx * cos(angle), end * length - length / 2, rz * sin(angle))
        }
        fun quad(a: Point, b: Point, c: Point, d: Point) {
            add(Triangle(a, b, c)); add(Triangle(a, c, d))
        }
        for (i in 0 until n) {
            quad(p(0,i,true), p(1,i,true), p(1,i+1,true), p(0,i+1,true))
            quad(p(0,i,false), p(0,i+1,false), p(1,i+1,false), p(1,i,false))
            quad(p(0,i,false), p(0,i,true), p(0,i+1,true), p(0,i+1,false))
            quad(p(1,i,false), p(1,i+1,false), p(1,i+1,true), p(1,i,true))
        }
        quad(p(0,0,false), p(1,0,false), p(1,0,true), p(0,0,true))
        quad(p(0,n,false), p(0,n,true), p(1,n,true), p(1,n,false))
    }

    fun stl(): String = buildString {
        appendLine("solid REFIT_DEMO_NOT_PATIENT_FIT_MM")
        for (t in triangles()) {
            val ux=t.b.x-t.a.x; val uy=t.b.y-t.a.y; val uz=t.b.z-t.a.z
            val vx=t.c.x-t.a.x; val vy=t.c.y-t.a.y; val vz=t.c.z-t.a.z
            val nx=uy*vz-uz*vy; val ny=uz*vx-ux*vz; val nz=ux*vy-uy*vx
            val norm=sqrt(nx*nx+ny*ny+nz*nz)
            appendLine(String.format(Locale.US," facet normal %.6f %.6f %.6f",nx/norm,ny/norm,nz/norm))
            appendLine("  outer loop")
            for (p in listOf(t.a,t.b,t.c)) appendLine(String.format(Locale.US,"   vertex %.6f %.6f %.6f",p.x,p.y,p.z))
            appendLine("  endloop\n endfacet")
        }
        appendLine("endsolid REFIT_DEMO_NOT_PATIENT_FIT_MM")
    }

    fun json(side: String, topY: Float, sideY: Float): String = """
        {
          "schema_version": 1,
          "data_mode": "demo",
          "measurement_source": "synthetic_fixture_not_image_analysis",
          "model_kind": "illustrative_open_cuff_not_final_orthosis",
          "units": "mm",
          "side": "$side",
          "wrist_width_mm": $wristWidth,
          "wrist_depth_mm": $wristDepth,
          "cuff_length_mm": $length,
          "shell_thickness_mm": $shell,
          "clearance_mm": $clearance,
          "guide_lines_normalized_y": {"top": $topY, "side": $sideY},
          "guide_lines_used_for_measurement": false,
          "suitable_for_patient_use": false
        }
    """.trimIndent()
}
