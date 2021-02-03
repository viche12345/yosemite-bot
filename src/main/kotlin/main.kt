import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

fun main(args: Array<String>) {
    Main().execute(args)
}

const val FACILITY_ID = 10086745

class Main {

    private val service = Retrofit.Builder()
        .baseUrl("https://www.recreation.gov")
        .addConverterFactory(GsonConverterFactory.create(
            GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
        ))
        .build()
        .create(ScraperService::class.java)

    fun execute(args: Array<String>) {
        val dateStrings =
            if (args.isEmpty()) {
                println("Enter dates in yyyy-MM-dd separated by spaces:")
                readLine()!!.split(" ")
            }
            else args.toList()
        verifyAllDatesAreSameMonth(dateStrings)

        while (true) {
            val response = service.getAvailability(
                FACILITY_ID,
                dateStrings[0].split("-")[0],
                dateStrings[0].split("-")[1]
            ).execute()
            require(response.body() != null) { "Error in response: ${response.errorBody()?.toString()}" }

            val dates = response.body()!!.dates
            dateStrings.forEach { dateString ->
                val reservable = dates[dateString]?.summaries?.values?.toList()?.get(0)?.reservable ?: 0
                if (reservable > 0) {
                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                    println("[${formatter.format(Date())}]\t$dateString AVAILABLE!!! Quantity: $reservable")
                }
            }

            Thread.sleep(500)
        }
    }

    private fun verifyAllDatesAreSameMonth(args: List<String>) {
        require(args.isNotEmpty()) { "Must provide dates in yyyy-MM-dd format" }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        var lastYearMonth: Pair<Int, Int>? = null
        args.forEach {
            val calendar = Calendar.getInstance().apply { time = dateFormat.parse(it) }
            lastYearMonth?.run {
                require(first == calendar.get(Calendar.YEAR)) { "Years do not match" }
                require(second == calendar.get(Calendar.MONTH)) { "Months do not match" }
            }
            lastYearMonth = Pair(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
        }
    }

    interface ScraperService {
        @GET("api/timedentry/availability/facility/{facility_id}/monthlyAvailabilitySummaryView?inventoryBucket=FIT")
        fun getAvailability(
            @Path("facility_id") facilityId: Int,
            @Query("year") year: String,
            @Query("month") month: String
        ) : Call<Availability>
    }

    data class Availability(
        @SerializedName("facility_availability_summary_view_by_local_date") val dates: HashMap<String, AvailabilityByDate>
    )

    data class AvailabilityByDate(
        val availabilityLevel: String,
        val facilityId: Int,
        val localDate: String,
        val reservedCount: Int,
        val scheduledCount: Int,
        @SerializedName("tour_availability_summary_view_by_tour_id") val summaries: HashMap<String, AvailabilitySummary>
    )

    data class AvailabilitySummary(
        val availabilityLevel: String,
        val facilityId: Int,
        val hasNotYetReleased: Boolean,
        val hasReservable: Boolean,
        val hasWalkUp: Boolean,
        val localDate: String,
        val nextReleaseTimestamp: String,
        val notYetReleased: Int,
        val reservable: Int,
        val reservedCount: Int,
        val scheduledCount: Int,
        val tourId: Int,
        val walkUp: Int
    )
}