import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val dateStrings =
        if (args.isEmpty()) {
            println("Enter dates in yyyy-MM-dd separated by spaces:")
            readLine()!!.split(" ")
        }
        else args.toList()
    verifyAllDatesAreSameMonth(dateStrings)
    println("OK! I'll begin scanning those dates now. Keep this window open and I'll update you when I find anything!")

    Main().getAvailableDatesPeriodic(dateStrings)
        .subscribe { day ->
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            println("[${formatter.format(Date())}]\t${day.dateString} AVAILABLE!!! Quantity: ${day.quantity}")
        }

    // Indefinitely hold the main thread since this is a command-line app
    CountDownLatch(1).await()
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

const val FACILITY_ID = 10086745
const val TOUR_ID = 10086746

class Main {

    private val executor = Executors.newSingleThreadExecutor()

    private val service = Retrofit.Builder()
        .baseUrl("https://www.recreation.gov")
        .addConverterFactory(GsonConverterFactory.create(
            GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
        ))
        .addCallAdapterFactory(RxJava3CallAdapterFactory.createSynchronous())
        .build()
        .create(ScraperService::class.java)

    fun getAvailableDatesPeriodic(dateStrings: List<String>): Observable<Result> =
        Observable.interval(1, TimeUnit.SECONDS, Schedulers.from(executor))
            .flatMap {
                // First, get availability from the monthly API
                service.getMonthlyAvailability(
                    FACILITY_ID,
                    dateStrings[0].split("-")[0],
                    dateStrings[0].split("-")[1]
                )
            }
            .flatMapIterable({ dateStrings }) { month, dateString ->
                // Return a summary object for each date that needs to be scanned
                month.dates[dateString]?.summaries?.get(TOUR_ID.toString())
                    ?: throw IllegalStateException("Month not found in response")
            }
            .flatMap ({
                // Check the daily API for a more accurate ticket count (updated monthly count seems to be always late)
                service.getDailyAvailability(FACILITY_ID, it.localDate)
            }) { summary, dailyAvailability ->
                val reservable = dailyAvailability[0].totalCount(summary.nextReleaseTimestamp?.let {
                    System.currentTimeMillis() >= SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(it).time
                } ?: true)
                Result(summary.localDate, reservable)
            }
            .filter { it.quantity > 0 }
            .doOnError { it.printStackTrace() }
            .retry()

    interface ScraperService {
        @GET("api/timedentry/availability/facility/{facility_id}/monthlyAvailabilitySummaryView?inventoryBucket=FIT")
        fun getMonthlyAvailability(
            @Path("facility_id") facilityId: Int,
            @Query("year") year: String,
            @Query("month") month: String
        ): Observable<MonthlyAvailability>

        @GET("api/timedentry/availability/facility/{facility_id}")
        fun getDailyAvailability(
            @Path("facility_id") facilityId: Int,
            @Query("date") date: String
        ): Observable<Array<DailyAvailability>>
    }

    data class Result(val dateString: String, val quantity: Int)

    data class MonthlyAvailability(
        @SerializedName("facility_availability_summary_view_by_local_date") val dates: HashMap<String, MonthlyAvailabilityByDate>
    )

    data class MonthlyAvailabilityByDate(
        val availabilityLevel: String,
        val facilityId: Int,
        val localDate: String,
        val reservedCount: Int,
        val scheduledCount: Int,
        @SerializedName("tour_availability_summary_view_by_tour_id") val summaries: HashMap<String, MonthlyAvailabilitySummary>
    )

    data class MonthlyAvailabilitySummary(
        val availabilityLevel: String,
        val facilityId: Int,
        val hasNotYetReleased: Boolean,
        val hasReservable: Boolean,
        val hasWalkUp: Boolean,
        val localDate: String,
        val nextReleaseTimestamp: String?,
        val notYetReleased: Int,
        val reservable: Int,
        val reservedCount: Int,
        val scheduledCount: Int,
        val tourId: Int,
        val walkUp: Int
    )

    data class DailyAvailability(
        val inventoryCount: Count,
        val reservationCount: Count
    ) {
        fun totalCount(includeSecondary: Boolean) = inventoryCount.any - reservationCount.any +
                if (includeSecondary) {
                    inventoryCount.anySecondary - reservationCount.anySecondary
                } else {
                    0
                }
    }

    data class Count(
        @SerializedName("ANY") val any: Int,
        @SerializedName("ANY_SECONDARY") val anySecondary: Int
    )
}