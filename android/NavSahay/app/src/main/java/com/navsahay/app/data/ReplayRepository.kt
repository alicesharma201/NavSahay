package com.navsahay.app.data

import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader

object ReplayRepository {

    private var cachedSamples: List<NavigationSample>? = null

    fun loadRoute(context: Context): List<NavigationSample> {
        cachedSamples?.let { return it }

        val samples = mutableListOf<NavigationSample>()
        try {
            val inputStream = context.assets.open("demo_route.json")
            val reader = InputStreamReader(inputStream)
            val jsonText = reader.readText()
            reader.close()
            inputStream.close()

            val jsonArray = JSONArray(jsonText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val gnssX = if (obj.isNull("gnss_x")) null else obj.getDouble("gnss_x")
                val gnssY = if (obj.isNull("gnss_y")) null else obj.getDouble("gnss_y")

                samples.add(
                    NavigationSample(
                        timestamp = obj.getDouble("timestamp"),
                        estimatedX = obj.getDouble("estimated_x"),
                        estimatedY = obj.getDouble("estimated_y"),
                        groundTruthX = obj.getDouble("ground_truth_x"),
                        groundTruthY = obj.getDouble("ground_truth_y"),
                        gnssX = gnssX,
                        gnssY = gnssY,
                        speedKmh = obj.getDouble("speed"),
                        errorMeters = obj.getDouble("error"),
                        uncertaintyMeters = obj.getDouble("uncertainty"),
                        gnssStatus = obj.getString("gnss_status")
                    )
                )
            }
            cachedSamples = samples
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return samples
    }
}
