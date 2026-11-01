package me.hufman.androidautoidrive.logging

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TripAnalyticsTest {
    private fun sample(time:Long,property:String,value:String,cached:Boolean=false)=JsonObject().apply {
        addProperty("type","sample");addProperty("timestamp",time);addProperty("property",property)
        addProperty("cached",cached);add("value",JsonParser.parseString(value))
    }
    @Test fun retainsPeaksAndExcludesCachedAndInvalidMeasurements(){
        val events=(0 until 10000).map{sample(1000L+it,"engine.temperature","""{"temperature":{"engine":${if(it==5000)120 else 90},"oil":85}}""")}
        val analysis=TripAnalytics.analyze((events+sample(12000,"engine.temperature","""{"temperature":{"engine":255}}""")+
            sample(13000,"engine.temperature","""{"temperature":{"engine":200}}""",true)).asSequence(),1000,15000)
        val points=analysis.series.getValue("engine").filter{it.value.isFinite()}
        assertEquals(120.0,points.maxOf{it.value},0.0)
        assertTrue(points.size<2500)
        assertTrue(analysis.series.getValue("engine").any{!it.value.isFinite()})
    }
    @Test fun splitsGpsGapsAndNeverInventsRoutesForOldTrips(){
        val events=listOf(
            sample(1000,"navigation.GPSPosition","""{"GPSPosition":{"latitude":52.1,"longitude":13.1}}"""),
            sample(2000,"navigation.GPSPosition","""{"GPSPosition":{"latitude":52.2,"longitude":13.2}}"""),
            sample(50000,"navigation.GPSPosition","""{"GPSPosition":{"latitude":52.3,"longitude":13.3}}"""),
            sample(51000,"navigation.GPSPosition","""{"GPSPosition":{"latitude":65535,"longitude":13.4}}"""),
            sample(52000,"navigation.GPSPosition","""{"GPSPosition":{"latitude":52.4,"longitude":13.4}}""",true))
        val analysis=TripAnalytics.analyze(events.asSequence(),1000,60000)
        assertEquals(listOf(2,1),analysis.route.map{it.size})
        val old=TripAnalytics.analyze(sequenceOf(sample(1000,"navigation.GPSExtendedInfo","""{"GPSExtendedInfo":{"altitude":85}}""")),1000,2000)
        assertTrue(old.route.isEmpty())
    }
    @Test fun statusValuesUseBandsAndPdcUsesCentimetres(){
        assertEquals(MetricKind.STATE,TripAnalytics.metrics.first{it.id=="gear"}.kind)
        assertEquals(MetricKind.STATE,TripAnalytics.metrics.first{it.id=="park"}.kind)
        assertEquals(MetricKind.CURVE,TripAnalytics.metrics.first{it.id=="oil"}.kind)
        assertEquals("cm",TripAnalytics.metrics.first{it.id=="pdcF0"}.unit)
    }
    @Test fun durationHandlesFinishedLiveAndInterruptedTrips(){
        assertEquals(5000L,TripLogStore.Trip("a",1000,6000,"beendet",0,0).durationMillis(9000))
        assertEquals(8000L,TripLogStore.Trip("a",1000,null,"läuft",0,0).durationMillis(9000))
        assertEquals(3000L,TripLogStore.Trip("a",1000,null,"unterbrochen",0,0,4000).durationMillis(9000))
    }
}
